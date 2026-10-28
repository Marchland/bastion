package dev.jacobandersen.bastion.media.storage

import java.io.InputStream

data class MediaUploadRequest(
    val originalFilename: String?,
    /** Used as the filename stem when no original filename is available. */
    val fallbackName: String?,
    val contentType: String?,
    val size: Long,
    val content: InputStream,
)
