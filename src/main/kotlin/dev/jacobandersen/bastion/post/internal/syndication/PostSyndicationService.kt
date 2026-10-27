package dev.jacobandersen.bastion.post.internal.syndication

import dev.jacobandersen.bastion.post.internal.entity.PostSyndicationEntity
import dev.jacobandersen.bastion.post.internal.model.PostSyndication
import dev.jacobandersen.bastion.post.internal.repository.PostSyndicationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PostSyndicationService(
    private val repository: PostSyndicationRepository,
) {
    /**
     * Applies the requested target set for a post. Targets that are no longer
     * requested are marked undesired instead of being deleted immediately, so
     * the reconciliation listener can retract any held copy first.
     */
    @Transactional
    fun syncDesiredTargets(
        postId: UUID,
        targets: List<SyndicationConfig.Target>,
    ) {
        val existing = repository.findByPostId(postId).associateBy { it.targetUid }

        targets.forEachIndexed { index, target ->
            val entity = existing[target.uid]
            if (entity == null) {
                repository.save(
                    PostSyndicationEntity(
                        postId = postId,
                        targetUid = target.uid,
                        targetName = target.name,
                        sortOrder = index,
                    ),
                )
            } else {
                entity.targetName = target.name
                entity.sortOrder = index
                entity.desired = true
                repository.save(entity)
            }
        }

        val desiredUids = targets.map { it.uid }.toSet()
        existing.values
            .filter { it.targetUid !in desiredUids }
            .forEach { entity ->
                entity.desired = false
                repository.save(entity)
            }
    }

    @Transactional
    fun recordOutcome(
        postId: UUID,
        targetUid: String,
        syndicatedUrl: String,
        sourceUrl: String,
    ) {
        val entity = repository.findByPostIdAndTargetUid(postId, targetUid) ?: return
        entity.syndicatedUrl = syndicatedUrl
        entity.sourceUrl = sourceUrl
        repository.save(entity)
    }

    /**
     * Clears the held-copy state after a retraction has been dispatched. A
     * later publish/reconcile will create the copy again.
     */
    @Transactional
    fun markRetracted(
        postId: UUID,
        targetUid: String,
    ) {
        val entity = repository.findByPostIdAndTargetUid(postId, targetUid) ?: return
        entity.syndicatedUrl = null
        entity.sourceUrl = null
        repository.save(entity)
    }

    @Transactional(readOnly = true)
    fun findByPostId(postId: UUID): List<PostSyndicationEntity> = repository.findByPostId(postId)

    /**
     * Published syndicated copies of a post, for readers such as the public API.
     * Target display names and ordering are snapshotted when the target is
     * recorded, so readers do not depend on the writer's configuration.
     */
    @Transactional(readOnly = true)
    fun findLinksByPostId(postId: UUID): List<PostSyndication> =
        repository
            .findByPostId(postId)
            .mapNotNull { entity ->
                entity.syndicatedUrl?.let { url ->
                    PostSyndication(
                        targetUid = entity.targetUid,
                        targetName = entity.targetName,
                        syndicatedUrl = url,
                        sortOrder = entity.sortOrder,
                    )
                }
            }.sortedWith(compareBy({ it.sortOrder }, { it.targetName }))

    @Transactional
    fun remove(
        postId: UUID,
        targetUid: String,
    ) {
        repository.deleteByPostIdAndTargetUid(postId, targetUid)
    }
}
