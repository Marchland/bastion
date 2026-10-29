package dev.jacobandersen.bastion.url

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.net.URI
import java.time.OffsetDateTime

/** The slug and date components parsed from a content URL. */
data class ContentUrlRef(
    val slug: String,
    val year: Int?,
    val month: Int?,
    val day: Int?,
)

/**
 * Builds and parses content URLs from a slug and date. The URL module owns the
 * path shape; callers map their domain objects to these primitives.
 */
@Service
class ContentUrlService(
    private val config: ContentUrlConfig,
) {
    @PostConstruct
    fun validatePathPattern() {
        if (!config.pathPattern.contains("{slug}")) {
            throw IllegalStateException("Content path pattern must contain at least {slug} to generate valid URLs")
        }
    }

    fun generateUrl(
        slug: String,
        date: OffsetDateTime,
    ): String = "${config.baseUrl.trimEnd('/')}/${generatePath(slug, date)}"

    fun generatePath(
        slug: String,
        date: OffsetDateTime,
    ): String =
        config.pathPattern
            .replace("{year}", date.year.toString())
            .replace("{month}", date.monthValue.toString().padStart(2, '0'))
            .replace("{day}", date.dayOfMonth.toString().padStart(2, '0'))
            .replace("{slug}", slug)

    fun extractRef(url: String): ContentUrlRef? {
        val parsedUrl = runCatching { URI(url) }.getOrNull() ?: return null
        val urlAuthority = UrlNormalizer.authority(url) ?: return null
        val baseAuthority = UrlNormalizer.authority(config.baseUrl) ?: return null

        // Lenient on scheme
        if (urlAuthority.host != baseAuthority.host || urlAuthority.port != baseAuthority.port) {
            return null
        }

        val patternParts = config.pathPattern.trim('/').split('/')
        val urlParts = parsedUrl.path.trim('/').split('/')
        if (urlParts.size < patternParts.size) {
            return null
        }

        val slugIndex = patternParts.indexOf("{slug}")
        if (slugIndex < 0) {
            return null
        }

        val slug = urlParts[slugIndex]
        if (slug.isBlank()) return null

        fun parsePart(name: String): Int? {
            val idx = patternParts.indexOf(name)
            if (idx < 0) return null
            return urlParts.getOrNull(idx)?.toIntOrNull()
        }

        return ContentUrlRef(
            slug = slug,
            year = parsePart("{year}"),
            month = parsePart("{month}"),
            day = parsePart("{day}"),
        )
    }

    fun extractSlug(url: String): String? = extractRef(url)?.slug

    /**
     * Whether a URL reference matches a content date. Only the date placeholders
     * present in the configured path pattern are checked.
     */
    fun matchesDate(
        ref: ContentUrlRef,
        date: OffsetDateTime?,
    ): Boolean {
        if (config.pathPattern.contains("{year}") && ref.year != date?.year) return false
        if (config.pathPattern.contains("{month}") && ref.month != date?.monthValue) return false
        if (config.pathPattern.contains("{day}") && ref.day != date?.dayOfMonth) return false
        return true
    }
}
