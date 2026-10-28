package dev.jacobandersen.bastion

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:latest"))

    @Bean
    fun redisContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT)

    @Bean
    fun redisProperties(
        @Qualifier("redisContainer") redisContainer: GenericContainer<*>,
    ): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar { registry ->
            registry.add("bastion.webmention.rate-limit.redis.uri") {
                "redis://${redisContainer.host}:${redisContainer.getMappedPort(REDIS_PORT)}"
            }
            registry.add("bastion.webmention.rate-limit.redis.database") { REDIS_DATABASE.toString() }
        }

    companion object {
        private const val REDIS_PORT = 6379
        private const val REDIS_DATABASE = 3
    }
}
