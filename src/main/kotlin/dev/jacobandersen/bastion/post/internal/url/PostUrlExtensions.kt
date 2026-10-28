package dev.jacobandersen.bastion.post.internal.url

import dev.jacobandersen.bastion.post.internal.model.Post
import dev.jacobandersen.bastion.url.ContentUrlService

internal fun ContentUrlService.generatePostUrl(post: Post): String =
    generateUrl(
        post.slug,
        post.publishedAt
            ?: throw IllegalStateException("Post '${post.slug}' has no parseable published timestamp"),
    )
