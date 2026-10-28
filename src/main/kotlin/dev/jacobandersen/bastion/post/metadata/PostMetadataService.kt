package dev.jacobandersen.bastion.post.metadata

import dev.jacobandersen.bastion.post.internal.metadata.PostTypesConfig
import dev.jacobandersen.bastion.post.internal.metadata.PostTypesRegistry
import dev.jacobandersen.bastion.post.internal.metadata.PropertiesRegistry
import org.springframework.stereotype.Service

/**
 * Query side for configured post types and their property metadata. Returns
 * protocol-neutral views; HTTP adapters are responsible for mapping them to a
 * wire shape.
 */
@Service
class PostMetadataService(
    private val postTypesRegistry: PostTypesRegistry,
    private val propertiesRegistry: PropertiesRegistry,
    private val postTypesConfig: PostTypesConfig,
) {
    fun postTypes(): List<PostTypeView> = postTypesRegistry.postTypes()

    fun allProperties(): List<PostPropertyView> = propertiesRegistry.allViews()

    /**
     * Returns the property views for a post type, or null when the type is
     * unknown.
     */
    fun propertiesFor(postType: String): List<PostPropertyView>? {
        val value = postType.lowercase()
        if (!postTypesRegistry.isKnownType(value)) return null

        val definition = postTypesConfig.postTypes.first { it.type == value }
        val names = postTypesConfig.commonProperties + definition.properties
        return propertiesRegistry.viewsFor(names)
    }
}
