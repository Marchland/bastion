package dev.jacobandersen.bastion.webmention.internal.config

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WebmentionRateLimitConfig(
    private val properties: WebmentionRateLimitProperties,
) {
    @Bean(destroyMethod = "shutdown")
    fun webmentionRateLimitRedisClient(): RedisClient {
        val base = RedisURI.create(properties.redis.uri)
        val builder =
            RedisURI
                .builder(base)
                .withDatabase(properties.redis.database)
                .withTimeout(properties.redis.timeout)

        if (properties.redis.ssl) {
            builder.withSsl(true)
        }

        val username = properties.redis.username
        val password = properties.redis.password
        if (!username.isNullOrBlank()) {
            builder.withAuthentication(username, password.orEmpty())
        } else if (!password.isNullOrBlank()) {
            builder.withPassword(password)
        }

        return RedisClient.create(builder.build())
    }

    @Bean(destroyMethod = "close")
    fun webmentionRateLimitRedisConnection(client: RedisClient): StatefulRedisConnection<String, ByteArray> {
        val codec: RedisCodec<String, ByteArray> = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
        return client.connect(codec)
    }

    @Bean
    fun webmentionRateLimitProxyManager(conn: StatefulRedisConnection<String, ByteArray>): ProxyManager<String> =
        Bucket4jLettuce
            .casBasedBuilder(conn)
            .expirationAfterWrite(
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(properties.keepAfterRefill),
            ).build()
}
