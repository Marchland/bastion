package dev.jacobandersen.bastion.config.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource

/**
 * Permissive CORS policy for the IndieAuth and Micropub endpoints. These are
 * called by browser clients from arbitrary origins, so they cannot use the
 * site's restrictive global CORS configuration.
 */
@Configuration
class PublicCorsConfig {
    @Bean("publicCorsConfigurationSource")
    fun publicCorsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOriginPatterns = listOf("*")
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = false
                maxAge = 3600L
            }
        return CorsConfigurationSource { _ -> config }
    }
}
