package dev.jacobandersen.bastion.url

import org.springframework.stereotype.Service

/**
 * Matches URLs against this site's content domain. Host and port must match
 * `bastion.content.base-url`; the scheme is intentionally lenient.
 */
@Service
class ContentUrlMatcher(
    private val config: ContentUrlConfig,
) {
    fun isOwnContentUrl(url: String): Boolean {
        val urlAuthority = UrlNormalizer.authority(url) ?: return false
        val baseAuthority = UrlNormalizer.authority(config.baseUrl) ?: return false
        return urlAuthority.host == baseAuthority.host && urlAuthority.port == baseAuthority.port
    }
}
