package dev.jacobandersen.bastion.media.storage

sealed interface MediaUploadResult {
    data class Success(
        val url: String,
    ) : MediaUploadResult

    data class Failure(
        val error: Throwable,
    ) : MediaUploadResult
}
