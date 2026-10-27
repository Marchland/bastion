package dev.jacobandersen.bastion.api.internal.tag.dto

import dev.jacobandersen.bastion.api.internal.dto.Pagination

data class TagListResponse(
    val tags: List<TagResponse>,
    val pagination: Pagination,
)
