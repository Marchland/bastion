package dev.jacobandersen.bastion.post.internal.syndication

import dev.jacobandersen.bastion.post.internal.entity.PostSyndicationEntity
import dev.jacobandersen.bastion.post.internal.repository.PostSyndicationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class PostSyndicationServiceTest {
    private val postId = UUID.randomUUID()
    private val repository = mock<PostSyndicationRepository>()
    private val service = PostSyndicationService(repository)

    @Test
    fun `findLinksByPostId excludes pending copies and sorts by configured order`() {
        whenever(repository.findByPostId(postId))
            .thenReturn(
                listOf(
                    PostSyndicationEntity(
                        postId = postId,
                        targetUid = "second",
                        targetName = "Second",
                        sortOrder = 2,
                        syndicatedUrl = "https://second.test/copy",
                    ),
                    PostSyndicationEntity(
                        postId = postId,
                        targetUid = "pending",
                        targetName = "Pending",
                        sortOrder = 0,
                        syndicatedUrl = null,
                    ),
                    PostSyndicationEntity(
                        postId = postId,
                        targetUid = "first",
                        targetName = "First",
                        sortOrder = 1,
                        syndicatedUrl = "https://first.test/copy",
                    ),
                ),
            )

        val links = service.findLinksByPostId(postId)

        assertEquals(listOf("first", "second"), links.map { it.targetUid })
        assertTrue(links.all { it.syndicatedUrl.isNotBlank() })
    }
}
