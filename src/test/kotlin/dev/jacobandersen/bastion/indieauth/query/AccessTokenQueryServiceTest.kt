package dev.jacobandersen.bastion.indieauth.query

import dev.jacobandersen.bastion.indieauth.internal.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.internal.data.entity.AccessTokenEntity
import dev.jacobandersen.bastion.indieauth.internal.data.repository.AccessTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.time.Instant

class AccessTokenQueryServiceTest {
    private val repository = mock(AccessTokenRepository::class.java)
    private val config = IndieAuthConfig(me = "https://example.com")
    private val service = AccessTokenQueryService(repository, config)

    private fun token(
        me: String = "https://example.com",
        expiresAt: Instant = Instant.now().plusSeconds(60),
    ) = AccessTokenEntity(
        tokenHash = "hash",
        me = me,
        clientId = "https://client.example",
        scope = "create media",
        issuedAt = Instant.now(),
        expiresAt = expiresAt,
    )

    @Test
    fun `resolve returns null for an unknown token`() {
        `when`(repository.findByTokenHash(any())).thenReturn(null)

        assertNull(service.resolve("raw-token"))
    }

    @Test
    fun `resolve returns null for an expired token`() {
        `when`(repository.findByTokenHash(any())).thenReturn(token(expiresAt = Instant.now().minusSeconds(1)))

        assertNull(service.resolve("raw-token"))
    }

    @Test
    fun `resolve returns null for a token for another identity`() {
        `when`(repository.findByTokenHash(any())).thenReturn(token(me = "https://someone.else"))

        assertNull(service.resolve("raw-token"))
    }

    @Test
    fun `resolve accepts normalized variants of the configured identity`() {
        `when`(repository.findByTokenHash(any())).thenReturn(token(me = "https://EXAMPLE.com/#frag"))

        val resolved = service.resolve("raw-token")

        assertEquals("https://EXAMPLE.com/#frag", resolved?.me)
    }

    @Test
    fun `resolve returns the token claims`() {
        `when`(repository.findByTokenHash(any())).thenReturn(token())

        val resolved = service.resolve("raw-token")

        assertEquals("https://example.com", resolved?.me)
        assertEquals("https://client.example", resolved?.clientId)
        assertEquals(listOf("create", "media"), resolved?.scope)
    }
}
