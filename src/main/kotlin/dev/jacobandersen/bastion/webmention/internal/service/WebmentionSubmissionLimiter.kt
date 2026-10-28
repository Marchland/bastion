package dev.jacobandersen.bastion.webmention.internal.service

import dev.jacobandersen.bastion.webmention.internal.config.WebmentionRateLimitProperties
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Guards the webmention receiver against submission flooding. The endpoint is
 * unauthenticated, and every accepted submission creates a database row and an
 * asynchronous verification job, so a hostile client could otherwise grow both
 * without bound.
 *
 * The buckets live in Redis, so the source window and pair cooldown are global
 * across Bastion replicas. The pair is checked first so a duplicate pair does
 * not consume a source token.
 */
@Service
class WebmentionSubmissionLimiter(
    private val proxyManager: ProxyManager<String>,
    private val properties: WebmentionRateLimitProperties,
) {
    private val sourceConfiguration =
        BucketConfiguration
            .builder()
            .addLimit { limit ->
                limit
                    .capacity(properties.sourceLimit)
                    .refillIntervally(properties.sourceLimit, properties.sourceWindow)
            }.build()

    private val pairConfiguration =
        BucketConfiguration
            .builder()
            .addLimit { limit ->
                limit
                    .capacity(properties.pairLimit)
                    .refillIntervally(properties.pairLimit, properties.pairCooldown)
            }.build()

    fun allow(
        sourceUrl: String,
        targetUrl: String,
    ): Boolean {
        val pairBucket = proxyManager.getProxy(pairKey(sourceUrl, targetUrl)) { pairConfiguration }
        if (!pairBucket.tryConsume(1)) return false

        val sourceBucket = proxyManager.getProxy(sourceKey(sourceUrl)) { sourceConfiguration }
        return sourceBucket.tryConsume(1)
    }

    private fun sourceKey(sourceUrl: String): String = "${properties.keyPrefix}:source:${hash(sourceUrl)}"

    private fun pairKey(
        sourceUrl: String,
        targetUrl: String,
    ): String = "${properties.keyPrefix}:pair:${hash("$sourceUrl\n$targetUrl")}"

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
