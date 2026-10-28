package dev.jacobandersen.bastion.url

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentUrlMatcherTest {
    private val matcher =
        ContentUrlMatcher(
            ContentUrlConfig(
                baseUrl = "https://test.jacobandersen.dev",
                pathPattern = "{year}/{month}/{day}/{slug}",
            ),
        )

    @Test
    fun identifiesOwnContentUrls() {
        assertTrue(matcher.isOwnContentUrl("https://test.jacobandersen.dev/2026/09/02/my-slug"))
        assertTrue(matcher.isOwnContentUrl("http://test.jacobandersen.dev/2026/09/02/my-slug"))
        assertTrue(matcher.isOwnContentUrl("https://TEST.JACOBANDERSEN.DEV/2026/09/02/my-slug"))
        assertTrue(matcher.isOwnContentUrl("https://test.jacobandersen.dev:443/2026/09/02/my-slug"))
    }

    @Test
    fun rejectsForeignUrlsAsOwn() {
        assertFalse(matcher.isOwnContentUrl("https://example.com/2026/09/02/my-slug"))
        assertFalse(matcher.isOwnContentUrl("https://evil.test.jacobandersen.dev/2026/09/02/my-slug"))
        assertFalse(matcher.isOwnContentUrl("https://test.jacobandersen.dev:8443/2026/09/02/my-slug"))
        assertFalse(matcher.isOwnContentUrl("not a url"))
    }
}
