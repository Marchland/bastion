package dev.jacobandersen.bastion.micropub.internal.command

import dev.jacobandersen.bastion.micropub.internal.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.commands.DeletePostCommand
import dev.jacobandersen.bastion.post.commands.PostCommandException
import dev.jacobandersen.bastion.post.commands.PostCommandService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class DeleteService(
    private val postCommandService: PostCommandService,
) {
    @PreAuthorize("hasAuthority('DELETE')")
    fun delete(payload: MicropubPayload): ApiResponse<*> = commonDelete(payload, true)

    @PreAuthorize("hasAuthority('UNDELETE')")
    fun undelete(payload: MicropubPayload): ApiResponse<*> = commonDelete(payload, false)

    private fun commonDelete(
        payload: MicropubPayload,
        delete: Boolean,
    ): ApiResponse<*> {
        val url =
            try {
                payload.getUrl()
            } catch (e: Exception) {
                return ApiResponse.Error.InvalidRequest(errorDescription = "URL missing: ${e.message}")
            }

        return try {
            postCommandService.delete(DeletePostCommand(url = url, deleted = delete))
            ApiResponse.Success.NoContent
        } catch (e: PostCommandException) {
            ApiResponse.Error.InvalidRequest(errorDescription = e.message ?: "Invalid post command")
        }
    }
}
