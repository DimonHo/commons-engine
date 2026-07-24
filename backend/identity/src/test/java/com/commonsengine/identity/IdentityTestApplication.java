package com.commonsengine.identity;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * identity 模块测试专用 Spring Boot 配置。
 *
 * <p>类在 {@code com.commonsengine.identity} 包下，{@code @SpringBootApplication}
 * 默认扫描 {@code com.commonsengine.identity.**} 全部子包，覆盖
 * {@code infrastructure.persistence} 下的 {@code @Entity} 和 Repository。
 */
@SpringBootApplication
public class IdentityTestApplication {
}
