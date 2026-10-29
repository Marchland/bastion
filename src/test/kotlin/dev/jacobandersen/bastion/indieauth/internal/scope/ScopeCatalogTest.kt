package dev.jacobandersen.bastion.indieauth.internal.scope

import dev.jacobandersen.bastion.indieauth.scopes.IndieAuthScopeContributor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ScopeCatalogTest {
    @Test
    fun `includes standard and contributed scopes`() {
        val catalog =
            ScopeCatalog(
                listOf(
                    IndieAuthScopeContributor { setOf("create", "update") },
                    IndieAuthScopeContributor { setOf("media") },
                ),
            )

        assertEquals(setOf("profile", "email", "create", "update", "media"), catalog.allowedScopes)
    }

    @Test
    fun `rejects scopes containing whitespace`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScopeCatalog(listOf(IndieAuthScopeContributor { setOf("bad scope") }))
        }
    }

    @Test
    fun `rejects blank scopes`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScopeCatalog(listOf(IndieAuthScopeContributor { setOf("") }))
        }
    }
}
