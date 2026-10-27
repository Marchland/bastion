package dev.jacobandersen.bastion.post.query

import dev.jacobandersen.microformats2.Mf2Object
import java.util.UUID

data class PostStateView(
    val id: UUID,
    val slug: String,
    val url: String,
    val h: String,
    val type: String?,
    val post: Mf2Object,
    val deleted: Boolean,
    val publiclyReachable: Boolean,
    val isFeedVisible: Boolean,
)

sealed interface PostQueryResult {
    data class Found(
        val post: PostStateView,
    ) : PostQueryResult

    data class Gone(
        val post: PostStateView,
    ) : PostQueryResult

    data object NotFound : PostQueryResult

    data class Invalid(
        val message: String,
    ) : PostQueryResult
}
