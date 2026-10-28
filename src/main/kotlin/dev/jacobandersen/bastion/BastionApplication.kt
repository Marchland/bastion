package dev.jacobandersen.bastion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableWebSecurity
@EnableMethodSecurity
@ConfigurationPropertiesScan
class BastionApplication

fun main(args: Array<String>) {
    runApplication<BastionApplication>(*args)
}
