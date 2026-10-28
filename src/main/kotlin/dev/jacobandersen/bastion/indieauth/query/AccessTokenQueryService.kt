package dev.jacobandersen.bastion.indieauth.query

import dev.jacobandersen.bastion.indieauth.internal.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.internal.data.repository.AccessTokenRepository
import dev.jacobandersen.bastion.indieauth.internal.security.Tokens
import dev.jacobandersen.bastion.url.UrlNormalizer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Token validation query used by peer modules such as Micropub. Resolves only
 * tokens issued for the configured Bastion identity. Token issuance, exchange,
 * refresh, and revocation stay inside the IndieAuth command side.
 */
@Service
class AccessTokenQueryService(
    private val accessTokenRepository: AccessTokenRepository,
    private val config: IndieAuthConfig,
) {
    @Transactional(readOnly = true)
    fun resolve(rawToken: String): ValidatedAccessToken? {
        val entity = accessTokenRepository.findByTokenHash(Tokens.sha256(rawToken)) ?: return null
        if (entity.expiresAt.isBefore(Instant.now())) {
            return null
        }

        val issued = entity.toDomain()
        if (!isConfiguredIdentity(issued.me)) {
            return null
        }

        return ValidatedAccessToken(
            me = issued.me,
            clientId = issued.clientId,
            scope = issued.scope,
            issuedAt = issued.issuedAt,
            expiresAt = issued.expiresAt,
        )
    }

    private fun isConfiguredIdentity(me: String): Boolean {
        val expected = UrlNormalizer.identity(config.me) ?: return false
        val actual = UrlNormalizer.identity(me) ?: return false
        return expected == actual
    }
}
