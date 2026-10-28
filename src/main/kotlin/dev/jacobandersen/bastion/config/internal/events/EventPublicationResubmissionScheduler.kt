package dev.jacobandersen.bastion.config.internal.events

import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Schedules the failed-publication resubmission job. JobRunr stores the
 * recurring job once and runs each occurrence on a single instance, so this is
 * safe when Bastion runs with multiple replicas.
 */
@Component
class EventPublicationResubmissionScheduler(
    private val jobScheduler: JobScheduler,
    private val resubmissionService: EventPublicationResubmissionService,
) {
    @PostConstruct
    fun scheduleRecurringResubmission() {
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, INTERVAL) {
            resubmissionService.resubmit()
        }
    }

    companion object {
        const val RECURRING_JOB_ID = "event-publication-resubmission"
        val INTERVAL: Duration = Duration.ofMinutes(1)
    }
}
