package dev.jacobandersen.bastion.indieauth.scopes

/**
 * Contributes scope names to the IndieAuth provider. The provider unions the
 * standard scopes with every contributor and uses that set for discovery and
 * authorization validation.
 *
 * Scope names are global, case-sensitive strings. Implementations should return
 * lowercase, space-free tokens. A module that contributes a scope is
 * responsible for enforcing it on its own resources.
 */
fun interface IndieAuthScopeContributor {
    fun scopes(): Set<String>
}
