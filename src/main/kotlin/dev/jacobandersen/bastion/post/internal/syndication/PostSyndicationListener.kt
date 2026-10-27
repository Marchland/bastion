package dev.jacobandersen.bastion.post.internal.syndication

import dev.jacobandersen.bastion.post.events.PostChangedEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val logger = KotlinLogging.logger {}

@Component
class PostSyndicationListener(
    private val syndicationService: SyndicationService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: PostChangedEvent) {
        try {
            syndicationService.reconcile(event.postId)
        } catch (e: Exception) {
            logger.error(e) { "Failed to reconcile syndication for post ${event.postId}" }
        }
    }
}
