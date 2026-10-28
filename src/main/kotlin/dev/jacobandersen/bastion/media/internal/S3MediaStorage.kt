package dev.jacobandersen.bastion.media.internal

import com.github.slugify.Slugify
import dev.jacobandersen.bastion.media.storage.MediaStorage
import dev.jacobandersen.bastion.media.storage.MediaUploadRequest
import dev.jacobandersen.bastion.media.storage.MediaUploadResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaTypeFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class S3MediaStorage(
    private val slugify: Slugify,
    private val s3Client: S3Client,
    private val mediaConfiguration: MediaConfiguration,
) : MediaStorage {
    override fun upload(request: MediaUploadRequest): MediaUploadResult {
        val fileId = UUID.randomUUID().toString()

        val originalName =
            request.originalFilename
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.takeIf { it.isNotBlank() }

        val fileExt =
            originalName
                ?.substringAfterLast('.', "")
                ?.takeIf { it.isNotBlank() }
                ?: "blob"

        val stem =
            originalName
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: request.fallbackName
                ?: "file"

        val safeStem = slugify.slugify(stem).ifBlank { "file" }
        val fileName = "$safeStem-$fileId.$fileExt"

        val contentType =
            request.contentType?.takeIf { it.isNotBlank() }
                ?: originalName?.let { MediaTypeFactory.getMediaType(it).orElse(null)?.toString() }

        return try {
            logger.info { "Uploading $fileName..." }

            val requestBuilder =
                PutObjectRequest
                    .builder()
                    .bucket(mediaConfiguration.s3.bucket)
                    .key(fileName)

            if (contentType != null) {
                requestBuilder.contentType(contentType)
            }

            s3Client.putObject(
                requestBuilder.build(),
                RequestBody.fromInputStream(request.content, request.size),
            )

            MediaUploadResult.Success("${mediaConfiguration.baseUrl.trimEnd('/')}/$fileName")
        } catch (e: Exception) {
            MediaUploadResult.Failure(e)
        }
    }
}
