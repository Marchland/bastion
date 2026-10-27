package dev.jacobandersen.bastion.indieauth.query

import dev.jacobandersen.bastion.indieauth.internal.data.repository.AccessTokenRepository
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class AccessTokenQueryServiceTest {
    private val repository = mock(AccessTokenRepository::class.java)
    private val service = AccessTokenQueryService(repository)

    @Test
    fun `resolve returns null for an unknown token`() {
        `when`(repository.findByTokenHash(any())).thenReturn(null)

        assertNull(service.resolve("raw-token"))
    }
}
