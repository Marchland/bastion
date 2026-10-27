package dev.jacobandersen.bastion.post.events

import java.time.Instant
import java.util.UUID

enum class PostChangeType {
    CREATED,
    UPDATED,
    DELETED,
    RESTORED,
}

/**
 * A fact about a post write. The event is intentionally small and carries no
 * post body: consumers load the latest state and reconcile idempotently, so
 * out-of-order or duplicated delivery cannot resurrect stale state.
 *
 * [wasFeedVisible] and [isFeedVisible] describe the public feed transition,
 * which lets WebSub decide whether the topic changed without loading the post.
 */
data class PostChangedEvent(
    val postId: UUID,
    val changeType: PostChangeType,
    val wasFeedVisible: Boolean,
    val isFeedVisible: Boolean,
    val occurredAt: Instant,
)
