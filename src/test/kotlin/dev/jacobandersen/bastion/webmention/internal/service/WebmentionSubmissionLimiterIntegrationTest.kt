package dev.jacobandersen.bastion.webmention.internal.service

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.webmention.internal.config.WebmentionRateLimitProperties
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "bastion.webmention.rate-limit.source-window=2s",
        "bastion.webmention.rate-limit.pair-cooldown=1s",
    ],
)
class WebmentionSubmissionLimiterIntegrationTest {
    @Autowired
    lateinit var limiter: WebmentionSubmissionLimiter

    @Autowired
    lateinit var properties: WebmentionRateLimitProperties

    @Autowired
    lateinit var connection: StatefulRedisConnection<String, ByteArray>

    private fun source(): String = "https://source-${UUID.randomUUID()}.example"

    private fun target(index: Int): String = "https://target-$index.example"

    private fun secondLimiter(): WebmentionSubmissionLimiter {
        val proxyManager =
            Bucket4jLettuce
                .casBasedBuilder(connection)
                .expirationAfterWrite(
                    ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(properties.keepAfterRefill),
                ).build()
        return WebmentionSubmissionLimiter(proxyManager, properties)
    }

    @Test
    fun `allows the first submission for a pair`() {
        assertTrue(limiter.allow(source(), target(0)))
    }

    @Test
    fun `rejects a repeated pair within the cooldown`() {
        val source = source()

        assertTrue(limiter.allow(source, target(0)))
        assertFalse(limiter.allow(source, target(0)))
    }

    @Test
    fun `allows a repeated pair after the cooldown expires`() {
        val source = source()

        assertTrue(limiter.allow(source, target(0)))
        Thread.sleep(properties.pairCooldown.toMillis() + 100)
        assertTrue(limiter.allow(source, target(0)))
    }

    @Test
    fun `allows distinct targets from one source up to the per-source cap`() {
        val source = source()

        repeat(properties.sourceLimit.toInt()) { index ->
            assertTrue(limiter.allow(source, target(index)), "submission $index should pass")
        }
    }

    @Test
    fun `rejects submissions from a source beyond the per-source cap`() {
        val source = source()
        repeat(properties.sourceLimit.toInt()) { index -> limiter.allow(source, target(index)) }

        assertFalse(limiter.allow(source, target(properties.sourceLimit.toInt())))
    }

    @Test
    fun `rejected duplicate pairs do not consume source tokens`() {
        val source = source()

        assertTrue(limiter.allow(source, target(0)))
        assertFalse(limiter.allow(source, target(0)))

        repeat(properties.sourceLimit.toInt() - 1) { index ->
            assertTrue(limiter.allow(source, target(index + 1)), "new pair $index should pass")
        }
    }

    @Test
    fun `resets the per-source cap when the window expires`() {
        val source = source()
        repeat(properties.sourceLimit.toInt()) { index -> limiter.allow(source, target(index)) }

        assertFalse(limiter.allow(source, target(properties.sourceLimit.toInt())))
        Thread.sleep(properties.sourceWindow.toMillis() + 100)
        assertTrue(limiter.allow(source, target(properties.sourceLimit.toInt())))
    }

    @Test
    fun `limits are shared across limiter instances`() {
        val source = source()
        val target = target(0)

        assertTrue(limiter.allow(source, target))
        assertFalse(secondLimiter().allow(source, target))
    }

    @Test
    fun `stores bucket state in the configured redis database`() {
        assertNotEquals(0, properties.redis.database)

        val source = source()
        assertTrue(limiter.allow(source, target(0)))
        val key = sourceKey(source)

        assertTrue(existsIn(properties.redis.database, key))
        assertFalse(existsIn(0, key))
    }

    @Test
    fun `expires idle bucket keys through redis ttl`() {
        val source = source()
        assertTrue(limiter.allow(source, target(0)))

        assertTrue(ttlIn(properties.redis.database, sourceKey(source)) > 0)
    }

    private fun sourceKey(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(StandardCharsets.UTF_8))
        return "${properties.keyPrefix}:source:${HexFormat.of().formatHex(digest)}"
    }

    private fun existsIn(
        database: Int,
        key: String,
    ): Boolean = withRedis(database) { it.exists(key) > 0 }

    private fun ttlIn(
        database: Int,
        key: String,
    ): Long = withRedis(database) { it.ttl(key) }

    private fun <T> withRedis(
        database: Int,
        block: (RedisCommands<String, String>) -> T,
    ): T {
        val uri =
            RedisURI
                .builder(RedisURI.create(properties.redis.uri))
                .withDatabase(database)
                .withTimeout(properties.redis.timeout)
                .build()
        val client = RedisClient.create(uri)
        return try {
            client.connect().use { connection -> block(connection.sync()) }
        } finally {
            client.shutdown()
        }
    }
}
