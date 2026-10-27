package dev.jacobandersen.bastion.websub.internal.listener

import dev.jacobandersen.bastion.post.events.PostChangedEvent
import dev.jacobandersen.bastion.websub.internal.WebsubPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val logger = KotlinLogging.logger {}

/**
 * Publishes the configured WebSub topic when the public feed changes.
 *
 * The WebSub specification requires publishers to inform their hubs when a
 * topic has been updated. Bastion's topic is the public feed, so a post that
 * enters or leaves the feed (including demotions and deletions) warrants a
 * publish notification. Draft and unlisted edits do not change the feed.
 */
@Component
class PostWebsubListener(
    private val websubPublisher: WebsubPublisher,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: PostChangedEvent) {
        if (!event.wasFeedVisible && !event.isFeedVisible) return

        try {
            logger.info { "Publishing WebSub for post ${event.postId} (${event.changeType})" }
            websubPublisher.publish()
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish WebSub for post ${event.postId}" }
        }
    }
}
