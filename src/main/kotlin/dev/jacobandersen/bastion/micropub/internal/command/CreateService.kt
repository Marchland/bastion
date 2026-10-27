package dev.jacobandersen.bastion.micropub.internal.command

import dev.jacobandersen.bastion.micropub.internal.media.FileUploadResult
import dev.jacobandersen.bastion.micropub.internal.media.FileUploadService
import dev.jacobandersen.bastion.micropub.internal.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.internal.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.commands.CreatePostCommand
import dev.jacobandersen.bastion.post.commands.PostCommandException
import dev.jacobandersen.bastion.post.commands.PostCommandService
import dev.jacobandersen.bastion.post.types.PostStatus
import dev.jacobandersen.bastion.post.types.PostVisibility
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import dev.jacobandersen.microformats2.texts
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class CreateService(
    private val postCommandService: PostCommandService,
    private val fileUploadService: FileUploadService,
    private val commandResolver: MicropubCommandResolver,
) {
    @PreAuthorize("hasAuthority('CREATE')")
    fun create(
        payload: MicropubPayload,
        files: MultiValueMap<String, MultipartFile>?,
    ): ApiResponse<*> {
        logger.info { "Parsing post payload..." }
        var obj = payload.asMf2Object()

        logger.info { "Resolving post commands..." }
        val commands =
            commandResolver.resolve { key -> obj.properties[key] }
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid command parameters in create")

        val requestedSyndicationUids = obj.texts(MicropubCommand.MP_SYNDICATE_TO)
        obj = obj.copy(properties = obj.properties.filterKeys { !MicropubCommand.isCommandProperty(it) })

        logger.info { "Uploading files (if any)..." }
        files?.entries?.forEach { (_, fileValues) ->
            fileValues.forEach { file ->
                try {
                    obj = uploadFile(obj, file)
                } catch (_: Exception) {
                    return ApiResponse.Error.Unknown(errorDescription = "file upload error")
                }
            }
        }

        val result =
            try {
                postCommandService.create(
                    CreatePostCommand(
                        post = obj,
                        requestedSlug = commands.slug,
                        status = commands.status ?: PostStatus.PUBLISHED,
                        visibility = commands.visibility ?: PostVisibility.PUBLIC,
                        syndicationTargetUids = requestedSyndicationUids,
                    ),
                )
            } catch (e: PostCommandException) {
                logger.warn { "Failed to create post: ${e.message}" }
                return ApiResponse.Error.InvalidRequest(errorDescription = e.message ?: "Invalid post command")
            }

        logger.info { "Post created: ${result.url}" }
        return ApiResponse.Success.Created(result.url)
    }

    private fun uploadFile(
        obj: Mf2Object,
        file: MultipartFile?,
    ): Mf2Object {
        if (file == null) return obj

        return when (val result = fileUploadService.upload(file)) {
            is FileUploadResult.Success -> {
                logger.info { "File uploaded successfully" }
                obj.addProperty(result.name, Mf2Value.String(result.url))
            }

            is FileUploadResult.Failure -> {
                logger.error(result.error) { "File upload failed" }
                throw result.error
            }
        }
    }
}
