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
import org.awaitility.Awaitility.await
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.util.UUID

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

    private fun awaitPending(
        postId: UUID,
        action: String,
    ) {
        await().atMost(Duration.ofSeconds(2)).untilAsserted {
            assertEquals(action, postSyndicationService.findByPostId(postId).single().pendingAction)
        }
    }

    private fun tokenOf(postId: UUID): UUID = requireNotNull(postSyndicationService.findByPostId(postId).single().pendingToken)

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
        awaitPending(result.id, "create")
        assertNull(postSyndicationService.findByPostId(result.id).single().syndicatedUrl)
        verify(jobScheduler, timeout(2_000)).enqueue<SyndicationService>(any<UUID>(), any())
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
        awaitPending(result.id, "create")

        syndicationService.runCreateJob(result.id, "bridgy", tokenOf(result.id))

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
        awaitPending(created.id, "create")
        syndicationService.runCreateJob(created.id, "bridgy", tokenOf(created.id))
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

        verify(jobScheduler, timeout(2_000)).enqueue<SyndicationService>(any<UUID>(), any())
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
        awaitPending(created.id, "create")
        syndicationService.runCreateJob(created.id, "bridgy", tokenOf(created.id))

        postCommandService.delete(DeletePostCommand(url = created.url, deleted = true))
        awaitPending(created.id, "delete")
        whenever(httpClient.sendDelete(any(), any())).thenReturn(SyndicationSendResult.Success(204, null))
        syndicationService.runDeleteJob(created.id, "bridgy", created.url, tokenOf(created.id))

        await().atMost(Duration.ofSeconds(2)).untilAsserted {
            assertNull(postSyndicationService.findByPostId(created.id).single().syndicatedUrl)
        }
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
        awaitPending(created.id, "create")
        syndicationService.runCreateJob(created.id, "bridgy", tokenOf(created.id))

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
        awaitPending(created.id, "delete")
        whenever(httpClient.sendDelete(any(), any())).thenReturn(SyndicationSendResult.Success(204, null))
        syndicationService.runDeleteJob(created.id, "bridgy", created.url, tokenOf(created.id))

        await().atMost(Duration.ofSeconds(2)).untilAsserted {
            assertTrue(postSyndicationService.findByPostId(created.id).isEmpty())
        }
    }

    @Test
    fun `failed creation is retried by reconciliation`() {
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
        awaitPending(created.id, "create")
        clearInvocations(jobScheduler)

        whenever(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Failure(500, "boom"))
        syndicationService.runCreateJob(created.id, "bridgy", tokenOf(created.id))

        syndicationService.reconcile(created.id, forceUpdate = false)

        verify(jobScheduler, timeout(2_000)).enqueue<SyndicationService>(any<UUID>(), any())
    }

    @Test
    fun `active claim suppresses duplicate dispatch`() {
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
        awaitPending(created.id, "create")
        whenever(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/copy/1"))
        syndicationService.runCreateJob(created.id, "bridgy", tokenOf(created.id))
        clearInvocations(jobScheduler)

        syndicationService.reconcile(created.id, forceUpdate = true)
        syndicationService.reconcile(created.id, forceUpdate = true)

        verify(jobScheduler, timeout(2_000).times(1)).enqueue<SyndicationService>(any<UUID>(), any())
    }
}
