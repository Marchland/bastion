package dev.jacobandersen.bastion.post.internal.syndication

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.post.commands.CreatePostCommand
import dev.jacobandersen.bastion.post.commands.DeletePostCommand
import dev.jacobandersen.bastion.post.commands.PostCommandService
import dev.jacobandersen.bastion.post.commands.PostPatch
import dev.jacobandersen.bastion.post.commands.SyndicationTargetChange
import dev.jacobandersen.bastion.post.commands.UpdatePostCommand
import dev.jacobandersen.bastion.post.types.PostStatus
import dev.jacobandersen.bastion.post.types.PostVisibility
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
        "bastion.micropub.syndication.targets[0].uid=bridgy",
        "bastion.micropub.syndication.targets[0].name=Bridgy",
        "bastion.micropub.syndication.targets[0].endpoint=https://bridgy.example/micropub",
        "bastion.micropub.syndication.targets[0].actions[0]=create",
        "bastion.micropub.syndication.targets[0].actions[1]=delete",
        "bastion.micropub.syndication.targets[0].actions[2]=update",
    ],
)
class SyndicationReconciliationTest {
    @Autowired
    lateinit var postCommandService: PostCommandService

    @Autowired
    lateinit var postSyndicationService: PostSyndicationService

    @Autowired
    lateinit var syndicationService: SyndicationService

    @MockitoBean
    lateinit var jobScheduler: JobScheduler

    @MockitoBean
    lateinit var httpClient: SyndicationHttpClient

    private fun entry(content: String) =
        Mf2Object(
            type = listOf("h-entry"),
            properties = mutableMapOf("content" to listOf(Mf2Value.String(content))),
            children = null,
        )

    private fun updatedPatch(content: String): PostPatch = PostPatch(replace = mapOf("content" to listOf(Mf2Value.String(content))))

    @Test
    fun `create records the desired target and enqueues creation`() {
        val result =
            postCommandService.create(
                CreatePostCommand(
                    post = entry("First body"),
                    requestedSlug = "syndication-${System.nanoTime()}",
                    status = PostStatus.PUBLISHED,
                    visibility = PostVisibility.PUBLIC,
                    syndicationTargetUids = listOf("bridgy"),
                ),
            )

        val record = postSyndicationService.findByPostId(result.id).single()
        assertEquals("bridgy", record.targetUid)
        assertTrue(record.desired)
        assertNull(record.syndicatedUrl)
        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())
    }

    @Test
    fun `successful creation records the copy and source url`() {
        val result =
            postCommandService.create(
                CreatePostCommand(
                    post = entry("First body"),
                    requestedSlug = "syndication-${System.nanoTime()}",
                    status = PostStatus.PUBLISHED,
                    visibility = PostVisibility.PUBLIC,
                    syndicationTargetUids = listOf("bridgy"),
                ),
            )
        whenever(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/copy/1"))

        syndicationService.runCreateJob(result.id, "bridgy")

        val record = postSyndicationService.findByPostId(result.id).single()
        assertEquals("https://brid.gy/copy/1", record.syndicatedUrl)
        assertEquals(result.url, record.sourceUrl)
    }

    @Test
    fun `update enqueues an update for a target holding a copy`() {
        val created =
            postCommandService.create(
                CreatePostCommand(
                    post = entry("First body"),
                    requestedSlug = "syndication-${System.nanoTime()}",
                    status = PostStatus.PUBLISHED,
                    visibility = PostVisibility.PUBLIC,
                    syndicationTargetUids = listOf("bridgy"),
                ),
            )
        whenever(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/copy/1"))
        syndicationService.runCreateJob(created.id, "bridgy")
        clearInvocations(jobScheduler)

        postCommandService.update(
            UpdatePostCommand(
                url = created.url,
                patch = updatedPatch("Updated body"),
                requestedSlug = null,
                status = null,
                visibility = null,
            ),
        )

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())
    }

    @Test
    fun `delete retracts the held copy`() {
        val created =
            postCommandService.create(
                CreatePostCommand(
                    post = entry("First body"),
                    requestedSlug = "syndication-${System.nanoTime()}",
                    status = PostStatus.PUBLISHED,
                    visibility = PostVisibility.PUBLIC,
                    syndicationTargetUids = listOf("bridgy"),
                ),
            )
        whenever(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/copy/1"))
        syndicationService.runCreateJob(created.id, "bridgy")

        postCommandService.delete(DeletePostCommand(url = created.url, deleted = true))

        assertNull(postSyndicationService.findByPostId(created.id).single().syndicatedUrl)
    }

    @Test
    fun `removing a target retracts and forgets it`() {
        val created =
            postCommandService.create(
                CreatePostCommand(
                    post = entry("First body"),
                    requestedSlug = "syndication-${System.nanoTime()}",
                    status = PostStatus.PUBLISHED,
                    visibility = PostVisibility.PUBLIC,
                    syndicationTargetUids = listOf("bridgy"),
                ),
            )
        whenever(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/copy/1"))
        syndicationService.runCreateJob(created.id, "bridgy")

        postCommandService.update(
            UpdatePostCommand(
                url = created.url,
                patch = updatedPatch("Updated body"),
                requestedSlug = null,
                status = null,
                visibility = null,
                syndicationTargets = SyndicationTargetChange(replace = emptyList()),
            ),
        )

        assertTrue(postSyndicationService.findByPostId(created.id).isEmpty())
    }
}
