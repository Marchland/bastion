package dev.jacobandersen.bastion.post.internal.repository

import dev.jacobandersen.bastion.post.internal.entity.PostSyndicationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PostSyndicationRepository : JpaRepository<PostSyndicationEntity, UUID> {
    fun findByPostId(postId: UUID): List<PostSyndicationEntity>

    fun findByPostIdAndTargetUid(
        postId: UUID,
        targetUid: String,
    ): PostSyndicationEntity?

    fun deleteByPostIdAndTargetUid(
        postId: UUID,
        targetUid: String,
    ): Long
}
