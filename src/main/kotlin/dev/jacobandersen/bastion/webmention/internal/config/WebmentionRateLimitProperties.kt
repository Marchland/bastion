package dev.jacobandersen.bastion.webmention.internal.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Webmention submission rate limits.
 *
 * Bucket4j stores the source and pair buckets in Redis so every replica sees
 * the same state. [keepAfterRefill] is how long an idle bucket is retained
 * after it has refilled; Redis TTL removes unused keys, so no cleanup job is
 * required.
 *
 * @property sourceLimit Submissions allowed per source within [sourceWindow].
 * @property sourceWindow Interval at which a source bucket refills to full.
 * @property pairLimit Submissions allowed per source/target pair within [pairCooldown].
 * @property pairCooldown Interval at which a pair bucket refills.
 * @property keyPrefix Namespace for all limiter keys, useful when Redis is shared.
 * @property keepAfterRefill Idle time before Redis expires a fully refilled bucket.
 * @property redis Connection settings for the shared Redis instance.
 */
@ConfigurationProperties(prefix = "bastion.webmention.rate-limit")
data class WebmentionRateLimitProperties(
    val sourceLimit: Long = 10,
    val sourceWindow: Duration = Duration.ofHours(1),
    val pairLimit: Long = 1,
    val pairCooldown: Duration = Duration.ofSeconds(30),
    val keyPrefix: String = "bastion:webmention:rate-limit",
    val keepAfterRefill: Duration = Duration.ofMinutes(5),
    val redis: Redis = Redis(),
) {
    /**
     * @property database Logical Redis database. Use a non-zero value when the
     *   Redis instance is shared with other applications. This takes
     *   precedence over any database path in [uri].
     */
    data class Redis(
        val uri: String = "redis://localhost:6379",
        val database: Int = 0,
        val username: String? = null,
        val password: String? = null,
        val ssl: Boolean = false,
        val timeout: Duration = Duration.ofSeconds(2),
    )

    init {
        require(sourceLimit > 0) { "bastion.webmention.rate-limit.source-limit must be positive" }
        require(pairLimit > 0) { "bastion.webmention.rate-limit.pair-limit must be positive" }
        require(!sourceWindow.isZero && !sourceWindow.isNegative) {
            "bastion.webmention.rate-limit.source-window must be positive"
        }
        require(!pairCooldown.isZero && !pairCooldown.isNegative) {
            "bastion.webmention.rate-limit.pair-cooldown must be positive"
        }
        require(keyPrefix.isNotBlank()) { "bastion.webmention.rate-limit.key-prefix must not be blank" }
        require(redis.database >= 0) { "bastion.webmention.rate-limit.redis.database must be non-negative" }
        require(!keepAfterRefill.isNegative) {
            "bastion.webmention.rate-limit.keep-after-refill must not be negative"
        }
    }
}
