package dev.jacobandersen.bastion.post.internal.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "post_syndications")
class PostSyndicationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "post_id", nullable = false)
    var postId: UUID,
    @Column(name = "target_uid", nullable = false)
    var targetUid: String,
    @Column(name = "target_name", nullable = false)
    var targetName: String = targetUid,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
    @Column(name = "syndicated_url", nullable = true)
    var syndicatedUrl: String? = null,
    @Column(name = "source_url", nullable = true)
    var sourceUrl: String? = null,
    @Column(name = "desired", nullable = false)
    var desired: Boolean = true,
    @Column(name = "created_at_utc", nullable = false)
    var createdAtUtc: Instant = Instant.now(),
) {
    constructor(postId: UUID, targetUid: String) : this(
        id = null,
        postId = postId,
        targetUid = targetUid,
    )

    constructor(postId: UUID, targetUid: String, targetName: String, sortOrder: Int) : this(
        id = null,
        postId = postId,
        targetUid = targetUid,
        targetName = targetName,
        sortOrder = sortOrder,
    )
}
