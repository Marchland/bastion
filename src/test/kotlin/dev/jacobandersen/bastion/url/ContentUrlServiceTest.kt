package dev.jacobandersen.bastion.url

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class ContentUrlServiceTest {
    private val service =
        ContentUrlService(
            ContentUrlConfig(
                baseUrl = "https://test.jacobandersen.dev",
                pathPattern = "{year}/{month}/{day}/{slug}",
            ),
        )

    @Test
    fun generatesUrlFromSlugAndDate() {
        assertEquals(
            "https://test.jacobandersen.dev/2026/09/02/my-slug",
            service.generateUrl("my-slug", OffsetDateTime.parse("2026-09-02T12:00:00Z")),
        )
    }

    @Test
    fun generatesPathFromSlugAndDate() {
        assertEquals(
            "2026/09/02/my-slug",
            service.generatePath("my-slug", OffsetDateTime.parse("2026-09-02T12:00:00Z")),
        )
    }

    @Test
    fun extractsSlugFromMatchingUrl() {
        assertEquals("my-slug", service.extractSlug("https://test.jacobandersen.dev/2026/09/02/my-slug"))
    }

    @Test
    fun extractsSlugWithTrailingSlash() {
        assertEquals("my-slug", service.extractSlug("https://test.jacobandersen.dev/2026/09/02/my-slug/"))
    }

    @Test
    fun extractsSlugFromUrlWithExtraPathSegments() {
        assertEquals("my-slug", service.extractSlug("https://test.jacobandersen.dev/2026/09/02/my-slug/extra"))
    }

    @Test
    fun extractsSlugFromUrlWithDifferentScheme() {
        assertEquals("my-slug", service.extractSlug("http://test.jacobandersen.dev/2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForForeignDomain() {
        assertNull(service.extractSlug("https://example.com/2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForLookalikeSubdomain() {
        assertNull(service.extractSlug("https://evil.test.jacobandersen.dev/2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForPathWithTooFewParts() {
        assertNull(service.extractSlug("https://test.jacobandersen.dev/2026/09"))
    }

    @Test
    fun returnsNullForRelativeUrl() {
        assertNull(service.extractSlug("2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForUnparseableUrl() {
        assertNull(service.extractSlug("not a url"))
    }

    @Test
    fun extractsSlugWithDefaultPortMatching() {
        assertEquals("my-slug", service.extractSlug("https://test.jacobandersen.dev:443/2026/09/02/my-slug"))
    }

    @Test
    fun extractsSlugWithCaseInsensitiveHost() {
        assertEquals("my-slug", service.extractSlug("https://TEST.JACOBANDERSEN.DEV/2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForNonDefaultPort() {
        assertNull(service.extractSlug("https://test.jacobandersen.dev:8443/2026/09/02/my-slug"))
    }

    @Test
    fun matchesDateWhenDatePlaceholdersMatch() {
        val ref = requireNotNull(service.extractRef("https://test.jacobandersen.dev/2026/09/02/my-slug"))

        assertTrue(service.matchesDate(ref, OffsetDateTime.parse("2026-09-02T23:00:00Z")))
    }

    @Test
    fun rejectsDateWhenDatePlaceholdersDiffer() {
        val ref = requireNotNull(service.extractRef("https://test.jacobandersen.dev/2026/09/02/my-slug"))

        assertFalse(service.matchesDate(ref, OffsetDateTime.parse("2026-09-03T00:00:00Z")))
    }

    @Test
    fun validatePathPatternRejectsMissingSlugPlaceholder() {
        val invalid =
            ContentUrlService(
                ContentUrlConfig(
                    baseUrl = "https://test.jacobandersen.dev",
                    pathPattern = "{year}/{month}/{day}",
                ),
            )

        assertThrows(IllegalStateException::class.java) {
            invalid.validatePathPattern()
        }
    }
}
