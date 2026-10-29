package dev.jacobandersen.bastion.micropub.internal.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MicropubScopeContributorTest {
    @Test
    fun `contributes every Micropub token scope`() {
        assertEquals(
            setOf("create", "update", "delete", "undelete", "media"),
            MicropubScopeContributor().scopes(),
        )
    }
}
