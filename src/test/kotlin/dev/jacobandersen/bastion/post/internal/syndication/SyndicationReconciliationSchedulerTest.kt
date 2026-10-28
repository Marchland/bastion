package dev.jacobandersen.bastion.post.internal.syndication

import org.jobrunr.jobs.lambdas.JobLambda
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class SyndicationReconciliationSchedulerTest {
    private val jobScheduler = mock<JobScheduler>()
    private val postSyndicationService = mock<PostSyndicationService>()
    private val syndicationService = mock<SyndicationService>()
    private val scheduler =
        SyndicationReconciliationScheduler(
            jobScheduler = jobScheduler,
            postSyndicationService = postSyndicationService,
            syndicationService = syndicationService,
        )

    @Test
    fun `reconciles every tracked post`() {
        val postIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        whenever(postSyndicationService.findPostIdsForReconciliation()).thenReturn(postIds)

        scheduler.reconcile()

        postIds.forEach { postId ->
            verify(syndicationService).reconcile(eq(postId), eq(false))
        }
    }

    @Test
    fun `registers a fixed recurring job id`() {
        scheduler.scheduleReconciliation()

        verify(jobScheduler).scheduleRecurrently(
            eq(SyndicationReconciliationScheduler.RECURRING_JOB_ID),
            eq(SyndicationReconciliationScheduler.INTERVAL),
            any<JobLambda>(),
        )
    }
}
