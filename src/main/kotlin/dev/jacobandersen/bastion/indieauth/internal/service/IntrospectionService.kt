package dev.jacobandersen.bastion.indieauth.internal.service

import dev.jacobandersen.bastion.indieauth.internal.type.IntrospectionResponse
import dev.jacobandersen.bastion.indieauth.internal.type.Scopes
import dev.jacobandersen.bastion.indieauth.query.AccessTokenQueryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Verifies access tokens for resource servers (IndieAuth 6, RFC 7662 plus
 * `me`). Tightly coupled callers such as Micropub keep using the local
 * [AccessTokenQueryService.resolve] lookup; this service shapes the same answer
 * into the interoperable introspection response.
 */
@Service
class IntrospectionService(
    private val accessTokenService: AccessTokenQueryService,
) {
    @Transactional(readOnly = true)
    fun introspect(rawToken: String): IntrospectionResponse {
        val issued = accessTokenService.resolve(rawToken) ?: return IntrospectionResponse(active = false)
        return IntrospectionResponse(
            active = true,
            me = issued.me,
            clientId = issued.clientId,
            scope = Scopes.join(issued.scope).takeIf { it.isNotBlank() },
            exp = issued.expiresAt.epochSecond,
            iat = issued.issuedAt.epochSecond,
        )
    }
}
