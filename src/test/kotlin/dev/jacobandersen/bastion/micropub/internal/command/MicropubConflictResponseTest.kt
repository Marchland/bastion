package dev.jacobandersen.bastion.micropub.internal.command

import dev.jacobandersen.bastion.micropub.internal.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.commands.PostCommandConflictException
import dev.jacobandersen.bastion.post.commands.PostCommandService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

class MicropubConflictResponseTest {
    private val postCommandService = mock<PostCommandService>()
    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    @Test
    fun `update conflict is reported as invalid_request`() {
        val commandResolver = mock<MicropubCommandResolver>()
        whenever(commandResolver.resolve(any())).thenReturn(PostCommands(null, null, null))
        whenever(postCommandService.update(any()))
            .thenThrow(PostCommandConflictException(CONFLICT_MESSAGE))
        val service = UpdateService(postCommandService, commandResolver)

        val response = service.update(updatePayload())

        val error = assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
        assertEquals("invalid_request", error.error)
        assertEquals(CONFLICT_MESSAGE, error.errorDescription)
    }

    @Test
    fun `delete conflict is reported as invalid_request`() {
        whenever(postCommandService.delete(any()))
            .thenThrow(PostCommandConflictException(CONFLICT_MESSAGE))
        val service = DeleteService(postCommandService)

        val response = service.delete(updatePayload())

        val error = assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
        assertEquals("invalid_request", error.error)
        assertEquals(CONFLICT_MESSAGE, error.errorDescription)
    }

    private fun updatePayload(): MicropubPayload.Json {
        val root =
            mapper.readTree(
                """
                {
                    "url": "https://example.test/notes/slug",
                    "replace": {"content": ["Updated body"]}
                }
                """,
            ) as ObjectNode
        return MicropubPayload.Json(root)
    }

    companion object {
        private const val CONFLICT_MESSAGE = "The post was modified by another request. Reload it and retry."
    }
}
