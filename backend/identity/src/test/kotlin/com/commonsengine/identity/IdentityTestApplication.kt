package com.commonsengine.identity

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * identity 模块测试专用 Spring Boot 配置
 *
 * 类在 com.commonsengine.identity 包下，
 * @SpringBootApplication 默认扫描 com.commonsengine.identity.** 全部子包，
 * 覆盖 infrastructure.persistence 下的 @Entity 和 Repository。
 */
@SpringBootApplication
class IdentityTestApplication
