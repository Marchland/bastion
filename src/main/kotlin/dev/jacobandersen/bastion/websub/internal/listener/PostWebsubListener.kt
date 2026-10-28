package dev.jacobandersen.bastion.websub.internal.listener

import dev.jacobandersen.bastion.post.events.PostChangedEvent
import dev.jacobandersen.bastion.websub.internal.WebsubPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

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
    @ApplicationModuleListener
    fun on(event: PostChangedEvent) {
        if (!event.wasFeedVisible && !event.isFeedVisible) return

        logger.info { "Publishing WebSub for post ${event.postId} (${event.changeType})" }
        websubPublisher.publish()
    }
}
