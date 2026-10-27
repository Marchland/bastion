package dev.jacobandersen.bastion.webmention.internal.listener

import dev.jacobandersen.bastion.post.events.PostChangeType
import dev.jacobandersen.bastion.post.events.PostChangedEvent
import dev.jacobandersen.bastion.post.query.PostQueryService
import dev.jacobandersen.bastion.webmention.internal.WebmentionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val logger = KotlinLogging.logger {}

/**
 * Reconciles outbound webmentions whenever a post changes. The listener loads
 * the latest post state instead of applying the event as a delta, so duplicate
 * and out-of-order events are harmless.
 */
@Component
class PostWebmentionListener(
    private val postQueryService: PostQueryService,
    private val webmentionService: WebmentionService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: PostChangedEvent) {
        try {
            val post = postQueryService.findById(event.postId) ?: return
            webmentionService.reconcile(post, refresh = event.changeType != PostChangeType.DELETED)
        } catch (e: Exception) {
            logger.error(e) { "Failed to reconcile webmentions for post ${event.postId}" }
        }
    }
}
