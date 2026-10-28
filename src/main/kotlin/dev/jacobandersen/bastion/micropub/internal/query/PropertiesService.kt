package dev.jacobandersen.bastion.micropub.internal.query

import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.metadata.PostMetadataService
import dev.jacobandersen.bastion.post.metadata.PostPropertyView
import org.springframework.stereotype.Service

@Service
class PropertiesService(
    private val postMetadataService: PostMetadataService,
) {
    fun getProperties(postType: String?): ApiResponse<*> {
        if (postType == null) {
            return ApiResponse.Success.Ok(mapOf("properties" to postMetadataService.allProperties().map(::toWire)))
        }

        val properties =
            postMetadataService.propertiesFor(postType)
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "unknown post type: $postType")

        return ApiResponse.Success.Ok(mapOf("properties" to properties.map(::toWire)))
    }

    private fun toWire(view: PostPropertyView): Map<String, Any?> =
        buildMap {
            put("name", view.name)
            put("hints", view.hints)
            put("display-name", view.displayName)
            if (view.options != null) put("options", view.options)
            if (view.defaultValue != null) put("default", view.defaultValue)
        }
}
