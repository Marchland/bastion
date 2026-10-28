package dev.jacobandersen.bastion.post.internal.syndication

import dev.jacobandersen.bastion.post.events.PostChangedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class PostSyndicationListener(
    private val syndicationService: SyndicationService,
) {
    @ApplicationModuleListener
    fun on(event: PostChangedEvent) {
        syndicationService.reconcile(event.postId, forceUpdate = true)
    }
}
