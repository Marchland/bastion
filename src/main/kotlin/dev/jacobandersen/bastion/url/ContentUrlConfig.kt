package dev.jacobandersen.bastion.url

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bastion.content")
data class ContentUrlConfig(
    val baseUrl: String,
    val pathPattern: String,
)
