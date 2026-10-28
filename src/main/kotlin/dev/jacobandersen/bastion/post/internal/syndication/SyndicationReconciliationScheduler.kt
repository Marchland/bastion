package dev.jacobandersen.bastion.post.internal.syndication

import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Periodically reconciles every post that has syndication state. This repairs
 * failed external dispatches and rows whose event was lost or whose claim
 * expired, without relying on another post change.
 *
 * The recurring job has a fixed JobRunr ID, so only one replica runs it per
 * occurrence. Each operation claims its own row and job token, so retries are
 * not deduplicated against an older, completed job.
 */
@Component
class SyndicationReconciliationScheduler(
    private val jobScheduler: JobScheduler,
    private val postSyndicationService: PostSyndicationService,
    private val syndicationService: SyndicationService,
) {
    @PostConstruct
    fun scheduleReconciliation() {
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, INTERVAL) {
            reconcile()
        }
    }

    fun reconcile() {
        postSyndicationService.findPostIdsForReconciliation().forEach { postId ->
            syndicationService.reconcile(postId, forceUpdate = false)
        }
    }

    companion object {
        const val RECURRING_JOB_ID = "syndication-reconciliation"
        val INTERVAL: Duration = Duration.ofMinutes(15)
    }
}
