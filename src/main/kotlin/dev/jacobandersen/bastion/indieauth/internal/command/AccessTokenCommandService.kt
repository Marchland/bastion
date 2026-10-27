package dev.jacobandersen.bastion.indieauth.internal.command

import dev.jacobandersen.bastion.indieauth.internal.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.internal.data.entity.AccessTokenEntity
import dev.jacobandersen.bastion.indieauth.internal.data.entity.AuthorizationCodeEntity
import dev.jacobandersen.bastion.indieauth.internal.data.repository.AccessTokenRepository
import dev.jacobandersen.bastion.indieauth.internal.data.repository.AuthorizationCodeRepository
import dev.jacobandersen.bastion.indieauth.internal.model.IssuedToken
import dev.jacobandersen.bastion.indieauth.internal.security.Pkce
import dev.jacobandersen.bastion.indieauth.internal.security.Tokens
import dev.jacobandersen.bastion.indieauth.internal.service.IndieAuthException
import dev.jacobandersen.bastion.indieauth.internal.type.IndieAuthError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Issues and resolves access tokens for the IndieAuth token endpoint and for
 * Micropub token validation. Tokens are stored as a SHA-256 hash only, so the
 * raw value cannot be recovered from the database.
 */
@Service
class AccessTokenCommandService(
    private val config: IndieAuthConfig,
    private val accessTokenRepository: AccessTokenRepository,
    private val authorizationCodeRepository: AuthorizationCodeRepository,
) {
    /**
     * Exchanges an authorization code for an access token, enforcing the
     * single-use, expiry, `client_id`, `redirect_uri` and PKCE guarantees.
     * Per 5.3.3 the token endpoint must not issue a token for an empty-scope
     * (login-only) code; those redeem at the authorization endpoint instead.
     */
    @Transactional
    fun exchange(
        code: String,
        clientId: String,
        redirectUri: String,
        codeVerifier: String?,
    ): IssuedToken {
        val codeHash = Tokens.sha256(code)
        val authorizationCode =
            authorizationCodeRepository.findByCodeHash(codeHash)
                ?: throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The authorization code is invalid")

        if (authorizationCode.expiresAt.isBefore(Instant.now())) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The authorization code has expired")
        }

        if (authorizationCode.scope.isBlank()) {
            throw IndieAuthException(
                IndieAuthError.Code.INVALID_GRANT,
                "This authorization code carries no scope and must be redeemed at the authorization endpoint",
            )
        }

        validateClient(authorizationCode, clientId, redirectUri)
        validatePkce(authorizationCode, codeVerifier)

        val claimed = authorizationCodeRepository.claim(codeHash, Instant.now())
        if (claimed == 0) {
            logger.warn { "Authorization code already used (possible replay)" }
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The authorization code has already been used")
        }

        val issued = issue(authorizationCode.me, authorizationCode.clientId, authorizationCode.scope)

        logger.info { "Issued access token for ${authorizationCode.me} (client ${authorizationCode.clientId})" }

        return issued
    }

    /**
     * Issues a fresh access token for an identity without consuming an
     * authorization code. Used by the refresh-token flow.
     */
    @Transactional
    fun issue(
        me: String,
        clientId: String,
        scope: String,
    ): IssuedToken {
        val rawToken = Tokens.random()
        val now = Instant.now()
        val expiresAt = now.plus(config.accessTokenTtl)
        accessTokenRepository.save(
            AccessTokenEntity(
                tokenHash = Tokens.sha256(rawToken),
                me = me,
                clientId = clientId,
                scope = scope,
                issuedAt = now,
                expiresAt = expiresAt,
            ),
        )

        return IssuedToken(
            accessToken = rawToken,
            scope = scope,
            me = me,
            expiresAt = expiresAt,
        )
    }

    /**
     * Revokes an access token by hash. Returns true when a row was removed;
     * unknown tokens report false so callers can decide on the response.
     */
    @Transactional
    fun revoke(rawToken: String): Boolean {
        val entity = accessTokenRepository.findByTokenHash(Tokens.sha256(rawToken)) ?: return false
        accessTokenRepository.delete(entity)
        return true
    }

    private fun validateClient(
        authorizationCode: AuthorizationCodeEntity,
        clientId: String,
        redirectUri: String,
    ) {
        if (authorizationCode.clientId != clientId) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The client_id does not match the issued code")
        }
        if (authorizationCode.redirectUri != redirectUri) {
            throw IndieAuthException(
                IndieAuthError.Code.INVALID_GRANT,
                "The redirect_uri does not match the issued code",
            )
        }
    }

    private fun validatePkce(
        authorizationCode: AuthorizationCodeEntity,
        codeVerifier: String?,
    ) {
        val challenge = authorizationCode.codeChallenge
        if (challenge.isNullOrBlank()) {
            if (!codeVerifier.isNullOrBlank()) {
                throw IndieAuthException(
                    IndieAuthError.Code.INVALID_GRANT,
                    "No code_verifier is expected for a code issued without a code_challenge",
                )
            }
            return
        }
        if (codeVerifier == null || !Pkce.verify(challenge, codeVerifier)) {
            throw IndieAuthException(
                IndieAuthError.Code.INVALID_GRANT,
                "The code_verifier does not match the code challenge",
            )
        }
    }
}
