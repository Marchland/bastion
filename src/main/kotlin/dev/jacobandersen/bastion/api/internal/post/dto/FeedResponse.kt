package dev.jacobandersen.bastion.api.internal.post.dto

import dev.jacobandersen.bastion.api.internal.dto.Pagination

data class FeedResponse(
    val items: List<PostResponse>,
    val pagination: Pagination,
)
