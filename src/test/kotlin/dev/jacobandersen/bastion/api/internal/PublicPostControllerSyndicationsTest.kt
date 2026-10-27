package dev.jacobandersen.bastion.api.internal

import dev.jacobandersen.bastion.api.internal.post.PublicPostController
import dev.jacobandersen.bastion.api.internal.post.dto.PostResponse
import dev.jacobandersen.bastion.post.read.PostFeedQuery
import dev.jacobandersen.bastion.post.read.PostLookup
import dev.jacobandersen.bastion.post.read.PostReadService
import dev.jacobandersen.bastion.post.read.PostView
import dev.jacobandersen.bastion.post.read.SyndicationLinkView
import dev.jacobandersen.bastion.webmention.read.WebmentionCountsView
import dev.jacobandersen.bastion.webmention.read.WebmentionReadService
import dev.jacobandersen.microformats2.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.UUID

class PublicPostControllerSyndicationsTest {
    private val postId = UUID.randomUUID()
    private val post =
        PostView(
            id = postId,
            slug = "slug",
            url = "https://bastion.test/2026/01/01/slug",
            h = "h-entry",
            type = null,
            published = "2026-01-01T00:00:00Z",
            updated = "2026-01-02T00:00:00Z",
            name = null,
            summary = emptyList(),
            content = emptyList(),
            contentHtml = emptyList(),
            category = emptyList(),
            properties =
                mapOf(
                    "published" to listOf(Mf2Value.String("2026-01-01T00:00:00Z")),
                    "updated" to listOf(Mf2Value.String("2026-01-02T00:00:00Z")),
                ),
        )

    private fun controller(links: List<SyndicationLinkView>): PublicPostController {
        val postReadService: PostReadService = mock()
        val webmentionReadService: WebmentionReadService = mock()
        whenever(postReadService.lookupBySlug("slug")).thenReturn(PostLookup.Found(post))
        whenever(webmentionReadService.countsForPost(postId)).thenReturn(WebmentionCountsView.EMPTY)
        whenever(webmentionReadService.verifiedByPost(postId)).thenReturn(emptyList())
        whenever(postReadService.syndicationLinks(postId)).thenReturn(links)
        return PublicPostController(postReadService, webmentionReadService)
    }

    private fun body(controller: PublicPostController): PostResponse {
        val response = controller.bySlug("slug")
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body as PostResponse
    }

    @Test
    fun `single lookup includes uid name and url for syndicated copies`() {
        val c =
            controller(
                links =
                    listOf(
                        SyndicationLinkView(
                            uid = "bridgy",
                            name = "Bridgy",
                            url = "https://brid.gy/copy/1",
                        ),
                    ),
            )
        val syndications = body(c).syndications
        assertEquals(1, syndications?.size)
        assertEquals("bridgy", syndications?.first()?.uid)
        assertEquals("Bridgy", syndications?.first()?.name)
        assertEquals("https://brid.gy/copy/1", syndications?.first()?.url)
    }

    @Test
    fun `feed leaves syndications null`() {
        val postReadService: PostReadService = mock()
        val webmentionReadService: WebmentionReadService = mock()
        whenever(postReadService.feed(any<PostFeedQuery>())).thenReturn(listOf(post))
        whenever(webmentionReadService.countsByPostIds(any())).thenReturn(emptyMap())
        val controller = PublicPostController(postReadService, webmentionReadService)

        val feed =
            controller.feed(null, null, null, limit = null, offset = null, year = null, month = null, day = null)

        assertEquals(1, feed.items.size)
        assertNull(feed.items.first().syndications)
    }
}
