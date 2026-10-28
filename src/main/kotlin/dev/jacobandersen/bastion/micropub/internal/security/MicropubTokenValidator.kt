package dev.jacobandersen.bastion.micropub.internal.security

import dev.jacobandersen.bastion.indieauth.query.AccessTokenQueryService
import org.springframework.stereotype.Component

/**
 * Validates a Micropub bearer token against the access tokens Bastion itself
 * issued (via the IndieAuth token endpoint). The IndieAuth query contract only
 * returns tokens for the configured Bastion identity.
 */
@Component
class MicropubTokenValidator(
    private val accessTokenQueryService: AccessTokenQueryService,
) {
    fun validateToken(rawToken: String): MicropubAuthentication {
        val issued =
            accessTokenQueryService.resolve(rawToken)
                ?: throw IllegalArgumentException("Access token is not valid")

        val scopes = issued.scope.mapNotNull { MicropubTokenScope.fromStringOrNull(it) }
        val token = MicropubToken(issued.me, issued.clientId, scopes)

        return MicropubAuthentication(rawToken, token, true)
    }
}
