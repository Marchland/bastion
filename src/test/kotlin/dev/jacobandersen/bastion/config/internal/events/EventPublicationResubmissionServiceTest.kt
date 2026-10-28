package dev.jacobandersen.bastion.config.internal.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.modulith.events.EventPublication
import org.springframework.modulith.events.FailedEventPublications
import org.springframework.modulith.events.ResubmissionOptions
import java.time.Duration

class EventPublicationResubmissionServiceTest {
    private val failedPublications = mock<FailedEventPublications>()
    private val service = EventPublicationResubmissionService(failedPublications)

    @Test
    fun `resubmits failed publications with bounded options`() {
        service.resubmit()

        val captor = argumentCaptor<ResubmissionOptions>()
        verify(failedPublications).resubmit(captor.capture())
        val options = captor.firstValue
        assertEquals(Duration.ofMinutes(2), options.minAge)
        assertEquals(EventPublicationResubmissionService.BATCH_SIZE, options.batchSize)
        assertEquals(EventPublicationResubmissionService.MAX_IN_FLIGHT, options.maxInFlight)
    }

    @Test
    fun `filter skips publications at the attempt limit`() {
        service.resubmit()

        val captor = argumentCaptor<ResubmissionOptions>()
        verify(failedPublications).resubmit(captor.capture())
        val filter = captor.firstValue.filter

        val belowLimit = mock<EventPublication>()
        whenever(belowLimit.completionAttempts).thenReturn(EventPublicationResubmissionService.MAX_ATTEMPTS - 1)
        val atLimit = mock<EventPublication>()
        whenever(atLimit.completionAttempts).thenReturn(EventPublicationResubmissionService.MAX_ATTEMPTS)

        assertTrue(filter.test(belowLimit))
        assertFalse(filter.test(atLimit))
    }
}
