package dev.jacobandersen.bastion.indieauth.internal.scope

import dev.jacobandersen.bastion.indieauth.scopes.IndieAuthScopeContributor
import org.springframework.stereotype.Service

/**
 * The complete scope vocabulary the IndieAuth provider accepts. Standard
 * provider scopes are always present; module scopes come from
 * [IndieAuthScopeContributor] beans.
 */
@Service
class ScopeCatalog(
    contributors: List<IndieAuthScopeContributor>,
) {
    val allowedScopes: Set<String> =
        buildSet {
            addAll(STANDARD_SCOPES)
            contributors.forEach { addAll(it.scopes()) }
        }

    init {
        require(allowedScopes.all { scope -> scope.isNotBlank() && scope.none { it.isWhitespace() } }) {
            "IndieAuth scopes must be non-blank tokens without whitespace"
        }
    }

    companion object {
        /** Scopes implemented by the IndieAuth provider itself. */
        val STANDARD_SCOPES: Set<String> = setOf("profile", "email")
    }
}
