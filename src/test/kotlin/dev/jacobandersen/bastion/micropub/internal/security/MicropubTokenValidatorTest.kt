package dev.jacobandersen.bastion.micropub.internal.security

import dev.jacobandersen.bastion.indieauth.query.AccessTokenQueryService
import dev.jacobandersen.bastion.indieauth.query.ValidatedAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

class MicropubTokenValidatorTest {
    private val me = "https://example.com"

    private val accessTokenService = mock(AccessTokenQueryService::class.java)
    private val validator = MicropubTokenValidator(accessTokenService)

    private fun issued(
        me: String = this.me,
        clientId: String = "https://client.example",
        scope: List<String> = listOf("create"),
    ) = ValidatedAccessToken(me, clientId, scope, Instant.now(), Instant.now().plusSeconds(60))

    @Test
    fun `resolves a valid token to an authentication with mapped scopes`() {
        `when`(accessTokenService.resolve("abc")).thenReturn(issued(scope = listOf("create", "media", "bogus")))

        val auth = validator.validateToken("abc")

        assertEquals(me, auth.getName())
        assertEquals(
            listOf(MicropubTokenScope.CREATE, MicropubTokenScope.MEDIA),
            (auth.getDetails() as MicropubToken).scope,
        )
    }

    @Test
    fun `rejects an unknown token`() {
        `when`(accessTokenService.resolve("abc")).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) { validator.validateToken("abc") }
    }
}
