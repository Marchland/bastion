package dev.jacobandersen.bastion.webmention.internal.listener

import dev.jacobandersen.bastion.post.events.PostChangeType
import dev.jacobandersen.bastion.post.events.PostChangedEvent
import dev.jacobandersen.bastion.post.query.PostQueryService
import dev.jacobandersen.bastion.webmention.internal.WebmentionService
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Reconciles outbound webmentions whenever a post changes. The listener loads
 * the latest post state instead of applying the event as a delta, so duplicate
 * and out-of-order events are harmless. Failures propagate so the Modulith
 * event publication registry can mark the publication failed and retry it.
 */
@Component
class PostWebmentionListener(
    private val postQueryService: PostQueryService,
    private val webmentionService: WebmentionService,
) {
    @ApplicationModuleListener
    fun on(event: PostChangedEvent) {
        val post = postQueryService.findById(event.postId) ?: return
        webmentionService.reconcile(post, refresh = event.changeType != PostChangeType.DELETED)
    }
}
