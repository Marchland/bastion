package dev.jacobandersen.bastion.post.commands

import dev.jacobandersen.microformats2.Mf2Value

/**
 * A protocol-neutral delta against a post's microformats document.
 *
 * [replace] sets or deletes properties (an empty value list deletes the
 * property). [add] appends values. [remove] removes specific values. [removeAll]
 * deletes whole properties.
 */
data class PostPatch(
    val replace: Map<String, List<Mf2Value>> = emptyMap(),
    val add: Map<String, List<Mf2Value>> = emptyMap(),
    val remove: Map<String, List<Mf2Value>> = emptyMap(),
    val removeAll: Set<String> = emptySet(),
)
