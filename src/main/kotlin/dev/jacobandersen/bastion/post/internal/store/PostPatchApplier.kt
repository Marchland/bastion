package dev.jacobandersen.bastion.post.internal.store

import dev.jacobandersen.bastion.post.commands.PostPatch
import dev.jacobandersen.microformats2.Mf2Object

/**
 * Applies a [PostPatch] to a post's microformats document. The order is
 * replace, add, remove, then removeAll. A replace with an empty value list
 * deletes the property.
 */
internal fun Mf2Object.applyPatch(patch: PostPatch): Mf2Object {
    var result = this

    patch.replace.forEach { (property, values) ->
        result =
            if (values.isEmpty()) {
                result.deleteProperty(property)
            } else {
                result.setProperty(property, values)
            }
    }

    patch.add.forEach { (property, values) ->
        result = result.addProperty(property, values)
    }

    patch.remove.forEach { (property, values) ->
        val remaining = result.getProperty(property).filter { it !in values }
        result =
            if (remaining.isNotEmpty()) {
                result.setProperty(property, remaining)
            } else {
                result.deleteProperty(property)
            }
    }

    patch.removeAll.forEach { property ->
        result = result.deleteProperty(property)
    }

    return result
}
