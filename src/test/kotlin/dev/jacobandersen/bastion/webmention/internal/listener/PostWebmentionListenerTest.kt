package dev.jacobandersen.bastion.webmention.internal.listener

import dev.jacobandersen.bastion.post.events.PostChangeType
import dev.jacobandersen.bastion.post.events.PostChangedEvent
import dev.jacobandersen.bastion.post.query.PostQueryService
import dev.jacobandersen.bastion.post.query.PostStateView
import dev.jacobandersen.bastion.webmention.internal.WebmentionService
import dev.jacobandersen.microformats2.Mf2Object
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class PostWebmentionListenerTest {
    private val postQueryService = mock<PostQueryService>()
    private val webmentionService = mock<WebmentionService>()
    private val listener = PostWebmentionListener(postQueryService, webmentionService)
    private val postId = UUID.randomUUID()

    private fun event() =
        PostChangedEvent(
            postId = postId,
            changeType = PostChangeType.UPDATED,
            wasFeedVisible = true,
            isFeedVisible = true,
            occurredAt = Instant.now(),
        )

    private fun post() =
        PostStateView(
            id = postId,
            slug = "post",
            url = "https://bastion.test/2026/01/01/post",
            h = "h-entry",
            type = null,
            post = Mf2Object(type = listOf("h-entry"), properties = emptyMap(), children = null),
            deleted = false,
            publiclyReachable = true,
            isFeedVisible = true,
        )

    @Test
    fun `skips reconciliation when the post no longer exists`() {
        whenever(postQueryService.findById(postId)).thenReturn(null)

        listener.on(event())

        verify(webmentionService, never()).reconcile(any(), any())
    }

    @Test
    fun `reconciliation failures propagate to the event registry`() {
        whenever(postQueryService.findById(postId)).thenReturn(post())
        whenever(webmentionService.reconcile(any(), any())).thenThrow(RuntimeException("webmention reconcile failed"))

        assertThrows(RuntimeException::class.java) {
            listener.on(event())
        }
    }
}
