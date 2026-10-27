package dev.jacobandersen.bastion.post.internal.model

/**
 * A published syndicated copy of a post. This is the post-owned read model;
 * target configuration remains in the writer that performs syndication.
 */
data class PostSyndication(
    val targetUid: String,
    val targetName: String,
    val syndicatedUrl: String,
    val sortOrder: Int,
)
