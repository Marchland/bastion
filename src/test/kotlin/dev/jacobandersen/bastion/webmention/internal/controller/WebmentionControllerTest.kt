package dev.jacobandersen.bastion.webmention.internal.controller

import dev.jacobandersen.bastion.post.query.PostQueryResult
import dev.jacobandersen.bastion.post.query.PostQueryService
import dev.jacobandersen.bastion.post.query.PostStateView
import dev.jacobandersen.bastion.post.url.UrlService
import dev.jacobandersen.bastion.webmention.internal.data.service.ReceivedWebmentionService
import dev.jacobandersen.bastion.webmention.internal.http.SourceHostValidator
import dev.jacobandersen.bastion.webmention.internal.service.WebmentionReceiverService
import dev.jacobandersen.bastion.webmention.internal.service.WebmentionSubmissionLimiter
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class WebmentionControllerTest {
    private lateinit var notificationService: ReceivedWebmentionService
    private lateinit var receiverService: WebmentionReceiverService
    private lateinit var postQueryService: PostQueryService
    private lateinit var urlService: UrlService
    private lateinit var jobScheduler: JobScheduler
    private lateinit var hostValidator: SourceHostValidator
    private lateinit var submissionLimiter: WebmentionSubmissionLimiter
    private lateinit var mockMvc: MockMvc

    private val sourceUrl = "https://source.example/reply"
    private val targetUrl = "https://test.jacobandersen.dev/2026/01/01/hello"
    private val postId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        notificationService = mock(ReceivedWebmentionService::class.java)
        receiverService = mock(WebmentionReceiverService::class.java)
        postQueryService = mock(PostQueryService::class.java)
        urlService = mock(UrlService::class.java)
        jobScheduler = mock(JobScheduler::class.java)
        hostValidator = SourceHostValidator { it.startsWith("http://127.0.0.1") }
        submissionLimiter = WebmentionSubmissionLimiter()

        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    WebmentionController(
                        notificationService = notificationService,
                        receiverService = receiverService,
                        postQueryService = postQueryService,
                        urlService = urlService,
                        jobScheduler = jobScheduler,
                        hostValidator = hostValidator,
                        submissionLimiter = submissionLimiter,
                    ),
                ).build()

        `when`(postQueryService.byUrl(targetUrl)).thenReturn(PostQueryResult.Found(publicPost("hello")))
    }

    @Test
    fun `accepts a valid webmention with 202 and records it as pending`() {
        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", sourceUrl)
                    .param("target", targetUrl),
            ).andExpect(status().isAccepted)

        verify(notificationService).ensurePending(sourceUrl, targetUrl, postId)
    }

    @Test
    fun `rejects a missing source`() {
        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("target", targetUrl),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))
    }

    @Test
    fun `rejects a malformed source url`() {
        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", "not a url")
                    .param("target", targetUrl),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects a non-http scheme`() {
        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", "mailto:someone@example.com")
                    .param("target", targetUrl),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects a source equal to the target`() {
        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", targetUrl)
                    .param("target", targetUrl),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects a target that is not a post on this site`() {
        val foreign = "https://other.example/2026/01/01/nope"
        `when`(postQueryService.byUrl(foreign))
            .thenReturn(PostQueryResult.Invalid("Invalid URL for this Bastion instance: $foreign"))

        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", sourceUrl)
                    .param("target", foreign),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))
    }

    @Test
    fun `rejects a target whose post is not publicly reachable`() {
        `when`(postQueryService.byUrl(targetUrl)).thenReturn(
            PostQueryResult.Found(postState(slug = "hello", publiclyReachable = false, isFeedVisible = false)),
        )

        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", sourceUrl)
                    .param("target", targetUrl),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects a target whose post is deleted`() {
        `when`(postQueryService.byUrl(targetUrl)).thenReturn(
            PostQueryResult.Gone(postState(slug = "hello", deleted = true, publiclyReachable = false, isFeedVisible = false)),
        )

        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", sourceUrl)
                    .param("target", targetUrl),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `accepts a multipart webmention`() {
        mockMvc
            .perform(
                multipart("/webmention")
                    .file(MockMultipartFile("part", "content".toByteArray()))
                    .param("source", sourceUrl)
                    .param("target", targetUrl),
            ).andExpect(status().isAccepted)

        verify(notificationService).ensurePending(sourceUrl, targetUrl, postId)
    }

    @Test
    fun `rejects a source on a blocked host`() {
        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", "http://127.0.0.1/internal")
                    .param("target", targetUrl),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rejects a source on the own content domain`() {
        val ownSource = "https://test.jacobandersen.dev/2026/01/01/other"
        `when`(urlService.isOwnContentUrl(ownSource)).thenReturn(true)

        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", ownSource)
                    .param("target", targetUrl),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))
    }

    @Test
    fun `rejects a submission over the rate limit`() {
        submissionLimiter.allow(sourceUrl, targetUrl, Instant.now())

        mockMvc
            .perform(
                post("/webmention")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("source", sourceUrl)
                    .param("target", targetUrl),
            ).andExpect(status().isBadRequest)
    }

    private fun publicPost(slug: String): PostStateView = postState(slug)

    private fun postState(
        slug: String,
        deleted: Boolean = false,
        publiclyReachable: Boolean = true,
        isFeedVisible: Boolean = true,
    ): PostStateView =
        PostStateView(
            id = postId,
            slug = slug,
            url = targetUrl,
            h = "h-entry",
            type = null,
            post =
                Mf2Object(
                    type = listOf("h-entry"),
                    properties =
                        mutableMapOf(
                            "name" to listOf(Mf2Value.String(slug)),
                            "published" to listOf(Mf2Value.String("2026-01-01T00:00:00Z")),
                        ),
                    children = null,
                ),
            deleted = deleted,
            publiclyReachable = publiclyReachable,
            isFeedVisible = isFeedVisible,
        )
}
