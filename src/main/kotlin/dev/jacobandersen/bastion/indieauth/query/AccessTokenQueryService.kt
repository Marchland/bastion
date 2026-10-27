package dev.jacobandersen.bastion.indieauth.query

import dev.jacobandersen.bastion.indieauth.internal.data.repository.AccessTokenRepository
import dev.jacobandersen.bastion.indieauth.internal.security.Tokens
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Token validation query used by peer modules such as Micropub. Token issuance,
 * exchange, refresh, and revocation stay inside the IndieAuth command side.
 */
@Service
class AccessTokenQueryService(
    private val accessTokenRepository: AccessTokenRepository,
) {
    @Transactional(readOnly = true)
    fun resolve(rawToken: String): ValidatedAccessToken? {
        val entity = accessTokenRepository.findByTokenHash(Tokens.sha256(rawToken)) ?: return null
        if (entity.expiresAt.isBefore(Instant.now())) {
            return null
        }

        val issued = entity.toDomain()
        return ValidatedAccessToken(
            me = issued.me,
            clientId = issued.clientId,
            scope = issued.scope,
            issuedAt = issued.issuedAt,
            expiresAt = issued.expiresAt,
        )
    }
}
