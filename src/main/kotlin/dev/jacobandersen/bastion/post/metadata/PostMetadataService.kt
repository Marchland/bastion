package dev.jacobandersen.bastion.post.metadata

import dev.jacobandersen.bastion.post.internal.metadata.PostTypesConfig
import dev.jacobandersen.bastion.post.internal.metadata.PostTypesRegistry
import dev.jacobandersen.bastion.post.internal.metadata.PropertiesRegistry
import org.springframework.stereotype.Service

/**
 * Query side for Micropub's `q=config` and `q=properties` endpoints. Keeps the
 * post type and property registries out of protocol adapters.
 */
@Service
class PostMetadataService(
    private val postTypesRegistry: PostTypesRegistry,
    private val propertiesRegistry: PropertiesRegistry,
    private val postTypesConfig: PostTypesConfig,
) {
    fun postTypesView(): List<Map<String, Any?>> = postTypesRegistry.postTypesView()

    fun allPropertiesView(): List<Map<String, Any?>> = propertiesRegistry.allViews()

    /**
     * Returns the property views for a post type, or null when the type is
     * unknown.
     */
    fun propertiesFor(postType: String): List<Map<String, Any?>>? {
        val value = postType.lowercase()
        if (!postTypesRegistry.isKnownType(value)) return null

        val definition = postTypesConfig.postTypes.first { it.type == value }
        val names = postTypesConfig.commonProperties + definition.properties
        return propertiesRegistry.viewsFor(names)
    }
}
