package dev.jacobandersen.bastion.post.internal.metadata

import dev.jacobandersen.bastion.post.metadata.PostTypeView
import org.springframework.stereotype.Service

/**
 * Single access point for the configured post types: validates the config
 * once at startup, exposes the known type names for query validation, and
 * returns protocol-neutral views with common properties merged in.
 */
@Service
class PostTypesRegistry(
    private val config: PostTypesConfig,
) {
    init {
        require(config.postTypes.isNotEmpty()) { "bastion.post-types.post-types must not be empty" }
        val names = config.postTypes.map { it.type }
        require(names.all { it.isNotBlank() }) { "every post type needs a non-blank type" }
        require(names.distinct().size == names.size) { "duplicate post type names in config" }
    }

    fun knownTypes(): Set<String> = config.postTypes.map { it.type }.toSet()

    fun isKnownType(type: String): Boolean = type in knownTypes()

    fun postTypes(): List<PostTypeView> =
        config.postTypes.map { def ->
            PostTypeView(
                type = def.type,
                name = def.name,
                h = def.h,
                properties = config.commonProperties + def.properties,
                requiredProperties = def.requiredProperties,
            )
        }
}
