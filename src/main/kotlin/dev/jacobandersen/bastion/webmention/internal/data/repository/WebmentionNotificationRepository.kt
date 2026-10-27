package dev.jacobandersen.bastion.webmention.internal.data.repository

import dev.jacobandersen.bastion.webmention.internal.data.domain.WebmentionState
import dev.jacobandersen.bastion.webmention.internal.data.entity.WebmentionNotificationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface WebmentionNotificationRepository : JpaRepository<WebmentionNotificationEntity, UUID> {
    fun findByPostId(postId: UUID): List<WebmentionNotificationEntity>

    fun findBySourceUrl(sourceUrl: String): List<WebmentionNotificationEntity>

    fun findBySourceUrlAndState(
        sourceUrl: String,
        state: WebmentionState,
    ): List<WebmentionNotificationEntity>

    fun findBySourceUrlAndTargetUrl(
        sourceUrl: String,
        targetUrl: String,
    ): WebmentionNotificationEntity?

    fun findByStateInAndDeliveredFalseAndNextAttemptAtNotNullAndNextAttemptAtLessThanEqual(
        states: Collection<WebmentionState>,
        nextAttemptAt: Instant,
    ): List<WebmentionNotificationEntity>
}
