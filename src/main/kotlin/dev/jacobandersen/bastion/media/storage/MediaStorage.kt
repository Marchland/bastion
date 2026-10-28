package dev.jacobandersen.bastion.media.storage

/**
 * Storage port for uploaded media. Callers own the request stream and must
 * close it; implementations consume but do not close it.
 */
interface MediaStorage {
    fun upload(request: MediaUploadRequest): MediaUploadResult
}
