package dev.jacobandersen.bastion.micropub.internal.query

import dev.jacobandersen.bastion.micropub.internal.type.resp.ApiResponse
import dev.jacobandersen.bastion.post.metadata.PostMetadataService
import org.springframework.stereotype.Service

@Service
class PropertiesService(
    private val postMetadataService: PostMetadataService,
) {
    fun getProperties(postType: String?): ApiResponse<*> {
        if (postType == null) {
            return ApiResponse.Success.Ok(mapOf("properties" to postMetadataService.allPropertiesView()))
        }

        val properties =
            postMetadataService.propertiesFor(postType)
                ?: return ApiResponse.Error.InvalidRequest(errorDescription = "unknown post type: $postType")

        return ApiResponse.Success.Ok(mapOf("properties" to properties))
    }
}
