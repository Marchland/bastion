package dev.jacobandersen.bastion.config.internal.events

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.modulith.events.FailedEventPublications
import org.springframework.modulith.events.ResubmissionOptions
import org.springframework.stereotype.Service
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Resubmits failed Modulith event publications. Scheduled from
 * [EventPublicationResubmissionScheduler] through JobRunr so only one instance
 * runs it at a time.
 */
@Service
class EventPublicationResubmissionService(
    private val failedPublications: FailedEventPublications,
) {
    fun resubmit() = resubmit(MIN_AGE)

    fun resubmit(minAge: Duration) {
        val options =
            ResubmissionOptions
                .defaults()
                .withMinAge(minAge)
                .withBatchSize(BATCH_SIZE)
                .withMaxInFlight(MAX_IN_FLIGHT)
                .withFilter { publication -> publication.completionAttempts < MAX_ATTEMPTS }

        logger.debug { "Resubmitting failed event publications older than $minAge" }
        failedPublications.resubmit(options)
    }

    companion object {
        val MIN_AGE: Duration = Duration.ofMinutes(2)
        const val BATCH_SIZE = 100
        const val MAX_IN_FLIGHT = 10
        const val MAX_ATTEMPTS = 5
    }
}
