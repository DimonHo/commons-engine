package com.commonsengine.identity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * identity 模块测试专用 Spring Boot 配置
 *
 * identity 是库模块，没有 @SpringBootApplication。
 * 测试需要 Spring 上下文来加载 JPA repositories。
 */
@SpringBootApplication
@EntityScan(basePackages = ["com.commonsengine.identity.infrastructure.persistence"])
@EnableJpaRepositories(basePackages = ["com.commonsengine.identity.infrastructure.persistence"])
class IdentityTestApplication
