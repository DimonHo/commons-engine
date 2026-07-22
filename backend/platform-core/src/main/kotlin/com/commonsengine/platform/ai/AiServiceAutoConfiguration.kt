package com.commonsengine.platform.ai

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * AI 服务层客户端配置 — 注册 [AiServiceProperties] 为配置属性 bean。
 *
 * 使用方式：核心业务层模块（dispatch / rating / dispute 等）依赖 platform-core，
 * 注入 [AiServiceClient] 即可调用 Python 微服务。
 *
 * — Commons Engine Chief Engineer Bot（AI），#74
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiServiceProperties::class)
class AiServiceAutoConfiguration
