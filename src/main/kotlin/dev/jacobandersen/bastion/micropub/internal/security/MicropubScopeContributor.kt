package dev.jacobandersen.bastion.micropub.internal.security

import dev.jacobandersen.bastion.indieauth.scopes.IndieAuthScopeContributor
import org.springframework.stereotype.Component

/**
 * Registers the Micropub scope vocabulary with IndieAuth. The enum remains the
 * source of truth for both the wire scopes and Spring Security authorities.
 */
@Component
class MicropubScopeContributor : IndieAuthScopeContributor {
    override fun scopes(): Set<String> = MicropubTokenScope.entries.map { it.name.lowercase() }.toSet()
}
