package dev.jacobandersen.bastion.post.internal.syndication

import dev.jacobandersen.bastion.post.events.PostChangeType
import dev.jacobandersen.bastion.post.events.PostChangedEvent
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class PostSyndicationListenerTest {
    private val syndicationService = mock<SyndicationService>()
    private val listener = PostSyndicationListener(syndicationService)

    @Test
    fun `reconciliation failures propagate to the event registry`() {
        val event =
            PostChangedEvent(
                postId = UUID.randomUUID(),
                changeType = PostChangeType.UPDATED,
                wasFeedVisible = true,
                isFeedVisible = true,
                occurredAt = Instant.now(),
            )
        whenever(syndicationService.reconcile(event.postId, event.occurredAt.toString()))
            .thenThrow(RuntimeException("syndication failed"))

        assertThrows(RuntimeException::class.java) {
            listener.on(event)
        }
    }
}
