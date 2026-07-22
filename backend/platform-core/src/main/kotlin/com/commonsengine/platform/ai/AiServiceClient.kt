package com.commonsengine.platform.ai

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Kotlin 侧 AI 服务客户端 — 核心业务层调用 Python 微服务的统一入口（#74）。
 *
 * 职责：
 * 1. **契约对齐**：DTO 与 `ai-services/common/models.py` 及三服务端点签名一一对应。
 *    Python 侧 snake_case JSON 通过 DTO 上的显式 `@JsonProperty` 映射；
 *    枚举小写值（如 `merchant_info`）通过 `@JsonCreator` 归一化兼容。
 * 2. **弹性**：每个调用包裹在 Resilience4j 重试 + 熔断器中。Python 服务不可用时
 *    返回 [AiFallbacks] 提供的安全降级值，而非向业务层抛出——AI 服务在阶段 2
 *    视为增强能力而非关键路径。
 * 3. **可观测**：每次调用记录 service / latency / outcome；降级时 WARN 级日志。
 *
 * 使用 Spring 默认配置的 RestClient（MessageConverters 由 Spring Boot 自动注入，
 * Jackson 3 与 Spring 7 的兼容桥接由框架处理），不在本类自定义 ObjectMapper。
 *
 * 设计原则对齐（ARCHITECTURE.md）：
 * - 算法透明（1.3）：降级 reason 显式标注来源
 * - 反榨取（3.5）：调度降级返回空建议，绝不饥饿派单
 * - 数据主权（1.5）：PII 检测不可用时保守标记待复审
 *
 * — Commons Engine Chief Engineer Bot（AI），#74
 */
@Component
class AiServiceClient(
    private val properties: AiServiceProperties,
) {
    private val logger = LoggerFactory.getLogger(AiServiceClient::class.java)

    /** 每个服务独立熔断器——一个服务故障不影响其他服务的调用。 */
    private val circuitBreakers: Map<AiService, CircuitBreaker> = AiService.entries.associateWith { svc ->
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .failureRateThreshold(properties.circuitBreaker.failureRateThreshold)
            .waitDurationInOpenState(Duration.ofSeconds(properties.circuitBreaker.waitDurationInOpenStateSec.toLong()))
            .slidingWindowSize(properties.circuitBreaker.slidingWindowSize)
            .minimumNumberOfCalls(properties.circuitBreaker.minimumNumberOfCalls)
            .build()
            .let { cfg -> CircuitBreaker.of(svc.slug, cfg) }
    }

    /** 每个服务独立重试器。 */
    private val retries: Map<AiService, Retry> = AiService.entries.associateWith { svc ->
        io.github.resilience4j.retry.RetryConfig.custom<Any>()
            .maxAttempts(properties.retry.maxAttempts)
            .waitDuration(Duration.ofMillis(properties.retry.waitDurationMs))
            .build()
            .let { cfg -> Retry.of(svc.slug, cfg) }
    }

    /** 各服务的 RestClient（按 base URL 预配置，复用 Spring 默认 MessageConverters）。 */
    private val restClients: Map<AiService, RestClient> = AiService.entries.associateWith { svc ->
        val endpoint = when (svc) {
            AiService.CUSTOMER_SERVICE -> properties.customerService
            AiService.CONTENT_MODERATION -> properties.contentModeration
            AiService.DISPATCH_OPTIMIZER -> properties.dispatchOptimizer
        }
        RestClient.builder()
            .baseUrl(endpoint.baseUrl)
            .requestFactory(requestFactory())
            .build()
    }

    private fun requestFactory(): JdkClientHttpRequestFactory =
        JdkClientHttpRequestFactory().apply {
            setReadTimeout(Duration.ofMillis(properties.readTimeoutMs.toLong()))
        }

    // ── 公开 API ──────────────────────────────────────────

    /** 客服对话。降级时返回转人工回复。 */
    fun chat(request: ChatRequest): ChatReply =
        callWithResilience(AiService.CUSTOMER_SERVICE, fallback = { AiFallbacks.customerServiceFallback(request.userId) }) {
            val start = System.nanoTime()
            val resp = restClients.getValue(AiService.CUSTOMER_SERVICE).post()
                .uri("/api/v1/customer-service/chat")
                .body(request)
                .retrieve()
                .body(apiResponseTypeRef<ChatReply>())
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            logger.info("ai chat ok service=customer-service latencyMs={} category={}", elapsedMs, resp?.data?.category)
            resp?.data ?: AiFallbacks.customerServiceFallback(request.userId)
        }

    /** 内容审核。降级时保守标记待复审（FLAGGED）。 */
    fun moderate(request: ModerationRequest): ModerationResult =
        callWithResilience(AiService.CONTENT_MODERATION, fallback = { AiFallbacks.moderationFallback() }) {
            val start = System.nanoTime()
            val resp = restClients.getValue(AiService.CONTENT_MODERATION).post()
                .uri("/api/v1/content-moderation/moderate")
                .body(request)
                .retrieve()
                .body(apiResponseTypeRef<ModerationResult>())
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            logger.info(
                "ai moderate ok service=content-moderation latencyMs={} decision={} category={}",
                elapsedMs, resp?.data?.decision, resp?.data?.category,
            )
            resp?.data ?: AiFallbacks.moderationFallback()
        }

    /** 调度建议。降级时返回空建议（交由 Kotlin 本地匹配引擎兜底）。 */
    fun suggestDispatch(request: DispatchRequest): DispatchResult =
        callWithResilience(AiService.DISPATCH_OPTIMIZER, fallback = { AiFallbacks.dispatchFallback() }) {
            val start = System.nanoTime()
            val resp = restClients.getValue(AiService.DISPATCH_OPTIMIZER).post()
                .uri("/api/v1/dispatch-optimizer/suggest")
                .body(request)
                .retrieve()
                .body(apiResponseTypeRef<DispatchResult>())
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            logger.info(
                "ai dispatch ok service=dispatch-optimizer latencyMs={} suggestions={}",
                elapsedMs, resp?.data?.suggestions?.size ?: 0,
            )
            resp?.data ?: AiFallbacks.dispatchFallback()
        }

    // ── 弹性包装 ──────────────────────────────────────────

    /**
     * 用熔断器 + 重试包裹一次远程调用；任何异常（含熔断器拒绝）都走 [fallback]。
     */
    private inline fun <T> callWithResilience(
        service: AiService,
        fallback: () -> T,
        crossinline call: () -> T,
    ): T {
        val cb = circuitBreakers.getValue(service)
        val retry = retries.getValue(service)
        return try {
            Retry.decorateSupplier(retry) {
                CircuitBreaker.decorateSupplier(cb) { call() }.get()
            }.get()
        } catch (e: CallNotPermittedException) {
            logger.warn("ai call blocked by open circuit service={} - using fallback", service.slug)
            fallback()
        } catch (e: Exception) {
            logger.warn("ai call failed service={} error={} - using fallback", service.slug, e.message)
            fallback()
        }
    }

    /** 构造 `ParameterizedTypeReference<ApiResponse<T>>`，保留泛型信息以供 Jackson 反序列化。 */
    private inline fun <reified T> apiResponseTypeRef(): ParameterizedTypeReference<ApiResponse<T>> =
        object : ParameterizedTypeReference<ApiResponse<T>>() {}
}
