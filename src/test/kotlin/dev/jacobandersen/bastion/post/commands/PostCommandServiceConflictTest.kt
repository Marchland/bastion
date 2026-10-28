package dev.jacobandersen.bastion.post.commands

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.post.internal.model.Post
import dev.jacobandersen.bastion.post.internal.store.PostService
import dev.jacobandersen.bastion.post.types.PostStatus
import dev.jacobandersen.bastion.post.types.PostVisibility
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
    ],
)
class PostCommandServiceConflictTest {
    @Autowired
    lateinit var service: PostCommandService

    @MockitoBean
    lateinit var postService: PostService

    @Test
    fun `stale update becomes a post command conflict`() {
        val post = post()
        whenever(postService.findBySlug("slug")).thenReturn(post)
        whenever(postService.updatePost(any(), any()))
            .thenThrow(ObjectOptimisticLockingFailureException("PostEntity", post.id))

        val exception =
            assertThrows(PostCommandConflictException::class.java) {
                service.update(
                    UpdatePostCommand(
                        url = "https://bastion.test/2026/10/29/slug",
                        patch = PostPatch(),
                        requestedSlug = null,
                        status = null,
                        visibility = null,
                    ),
                )
            }

        assertEquals("The post was modified by another request. Reload it and retry.", exception.message)
    }

    private fun post() =
        Post(
            id = UUID.randomUUID(),
            slug = "slug",
            status = PostStatus.PUBLISHED,
            visibility = PostVisibility.PUBLIC,
            deleted = false,
            h = "h-entry",
            type = "note",
            post =
                Mf2Object(
                    type = listOf("h-entry"),
                    properties = mutableMapOf("content" to listOf(Mf2Value.String("Body"))),
                    children = null,
                ),
        )
}
