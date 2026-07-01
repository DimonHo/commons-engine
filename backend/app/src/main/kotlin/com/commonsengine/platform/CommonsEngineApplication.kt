package com.commonsengine.platform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.commonsengine"])
class CommonsEngineApplication

fun main(args: Array<String>) {
    runApplication<CommonsEngineApplication>(*args)
}
