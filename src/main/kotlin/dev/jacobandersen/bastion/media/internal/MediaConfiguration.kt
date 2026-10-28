package dev.jacobandersen.bastion.media.internal

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bastion.media")
data class MediaConfiguration(
    val baseUrl: String,
    val s3: S3Configuration,
) {
    data class S3Configuration(
        val accessKeyId: String,
        val secretAccessKey: String,
        val endpoint: String,
        val region: String,
        val bucket: String,
    )
}
