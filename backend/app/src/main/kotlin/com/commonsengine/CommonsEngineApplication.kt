package com.commonsengine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 公地引擎启动入口
 *
 * 主类放在 com.commonsengine 根包下，
 * Spring Boot 默认扫描 com.commonsengine.** 全部子包，
 * 覆盖所有模块的 @Entity、Repository、@Component。
 */
@SpringBootApplication
open class CommonsEngineApplication

fun main(args: Array<String>) {
    runApplication<CommonsEngineApplication>(*args)
}
