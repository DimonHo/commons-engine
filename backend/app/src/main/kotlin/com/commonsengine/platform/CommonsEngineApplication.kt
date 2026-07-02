package com.commonsengine.platform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.commonsengine"])
@EntityScan(basePackages = ["com.commonsengine"])
@EnableJpaRepositories(basePackages = ["com.commonsengine"])
open class CommonsEngineApplication

fun main(args: Array<String>) {
    runApplication<CommonsEngineApplication>(*args)
}
