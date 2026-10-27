package dev.jacobandersen.bastion.indieauth.query

import java.time.Instant

/**
 * A resolved, unexpired access token as seen by peer modules. Scopes remain
 * opaque strings; protocol adapters map them to their own scope types.
 */
data class ValidatedAccessToken(
    val me: String,
    val clientId: String,
    val scope: List<String>,
    val issuedAt: Instant,
    val expiresAt: Instant,
)
