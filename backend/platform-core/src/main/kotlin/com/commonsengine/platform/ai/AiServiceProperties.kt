package com.commonsengine.platform.ai

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * AI 服务层连接配置。
 *
 * 三个 Python 微服务的 base URL（默认本地开发地址）。生产环境通过
 * 环境变量覆盖（如 `AI_SERVICES_CUSTOMER_SERVICE_BASE_URL`）。
 *
 * 熔断与重试参数遵循 Resilience4j 语义，默认值面向「AI 服务非关键路径」
 * 的保守策略：快速失败、有限重试、降级而非抛出。
 *
 * — Commons Engine Chief Engineer Bot（AI），#74
 */
@ConfigurationProperties(prefix = "ai-services")
data class AiServiceProperties(

    /** 各服务 base URL（无尾斜杠）。 */
    val customerService: ServiceEndpoint = ServiceEndpoint(baseUrl = "http://localhost:8001"),
    val contentModeration: ServiceEndpoint = ServiceEndpoint(baseUrl = "http://localhost:8002"),
    val dispatchOptimizer: ServiceEndpoint = ServiceEndpoint(baseUrl = "http://localhost:8003"),

    /** 连接超时（毫秒）。 */
    val connectTimeoutMs: Int = 1_000,

    /** 读取超时（毫秒）— AI 推理可能较慢，略宽于普通 HTTP。 */
    val readTimeoutMs: Int = 3_000,

    val circuitBreaker: CircuitBreakerConfig = CircuitBreakerConfig(),
    val retry: RetryConfig = RetryConfig(),
) {
    data class ServiceEndpoint(val baseUrl: String)

    data class CircuitBreakerConfig(
        /** 失败率阈值百分比（0-100），达到后打开熔断器。 */
        val failureRateThreshold: Float = 50f,
        /** 熔断器打开后等待多久进入半开状态（秒）。 */
        val waitDurationInOpenStateSec: Int = 30,
        /** 滑动窗口大小（调用次数）。 */
        val slidingWindowSize: Int = 20,
        /** 计算失败率所需的最少调用数。 */
        val minimumNumberOfCalls: Int = 10,
    )

    data class RetryConfig(
        val maxAttempts: Int = 2,
        val waitDurationMs: Long = 200,
    )
}
