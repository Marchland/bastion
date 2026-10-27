package dev.jacobandersen.bastion.post.commands

import dev.jacobandersen.bastion.post.types.PostStatus
import dev.jacobandersen.bastion.post.types.PostVisibility
import dev.jacobandersen.microformats2.Mf2Object
import java.util.UUID

data class CreatePostCommand(
    val post: Mf2Object,
    val requestedSlug: String?,
    val status: PostStatus,
    val visibility: PostVisibility,
    val syndicationTargetUids: List<String> = emptyList(),
)

data class SyndicationTargetChange(
    /** When present, this is the complete desired target set. */
    val replace: List<String>? = null,
    val add: List<String> = emptyList(),
    val remove: List<String> = emptyList(),
    val removeAll: Boolean = false,
)

data class UpdatePostCommand(
    val url: String,
    val post: Mf2Object,
    val requestedSlug: String?,
    val status: PostStatus?,
    val visibility: PostVisibility?,
    /** Null means leave the requested target set unchanged. */
    val syndicationTargets: SyndicationTargetChange? = null,
)

data class DeletePostCommand(
    val url: String,
    val deleted: Boolean,
)

data class PostCommandResult(
    val id: UUID,
    val slug: String,
    val url: String,
    val publiclyReachable: Boolean,
    val deleted: Boolean,
    val slugChanged: Boolean = false,
)

class PostCommandException(
    message: String,
) : RuntimeException(message)
