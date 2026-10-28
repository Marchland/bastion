package dev.jacobandersen.bastion.indieauth.internal.type

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScopesTest {
    @Test
    fun `parse drops blanks and duplicates`() {
        assertEquals(listOf("create", "update"), Scopes.parse("create  update create "))
    }

    @Test
    fun `join drops blanks and duplicates`() {
        assertEquals("create update", Scopes.join(listOf("create", "", "update", "create")))
    }
}
