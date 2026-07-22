package com.commonsengine.platform.ai

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * AI 服务层客户端 DTO — 与 Python 侧 `ai-services/common/models.py` 契约对齐。
 *
 * 三个 Python 微服务（customer-service / content-moderation / dispatch-optimizer）
 * 统一返回 [ApiResponse] 包装，本模块用同一套反序列化逻辑消费。
 *
 * 字段命名通过显式 [JsonProperty] 映射 Python 侧 snake_case；
 * Kotlin data class 构造参数注解需同时指向 `param` 与 `field`，Jackson 才能在
 * 序列化（读字段）与反序列化（写构造参数）双向都识别契约名。
 * 枚举小写值（如 `merchant_info`）通过 [JsonCreator] 归一化兼容。
 *
 * — Commons Engine Chief Engineer Bot（AI），#74
 */

/**
 * 统一成功响应包装 — 对应 Python `ApiResponse[T]`。
 *
 * `success` 默认为 true；`data` 在 Python 侧为 `T | None`，Kotlin 侧用可空类型表达。
 */
data class ApiResponse<T>(
    @param:JsonProperty("success") @field:JsonProperty("success") val success: Boolean = true,
    @param:JsonProperty("data") @field:JsonProperty("data") val data: T? = null,
    @param:JsonProperty("message") @field:JsonProperty("message") val message: String? = null,
)

/**
 * 统一错误响应 — 对应 Python `ErrorResponse`。
 * error_code 格式 `<service>.<reason>`，便于跨服务追踪。
 */
data class AiErrorResponse(
    @param:JsonProperty("success") @field:JsonProperty("success") val success: Boolean = false,
    @param:JsonProperty("error_code") @field:JsonProperty("error_code") val errorCode: String,
    @param:JsonProperty("message") @field:JsonProperty("message") val message: String,
)

/**
 * AI 服务调用异常 — 当 Python 服务返回非 2xx、或反序列化失败、或熔断降级时抛出。
 */
class AiServiceException(
    val service: AiService,
    val statusCode: Int? = null,
    val errorCode: String? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** AI 服务标识 — 用于异常归因与日志。 */
enum class AiService(val slug: String) {
    CUSTOMER_SERVICE("customer-service"),
    CONTENT_MODERATION("content-moderation"),
    DISPATCH_OPTIMIZER("dispatch-optimizer"),
}

// ── customer-service DTOs ──────────────────────────────

data class ChatRequest(
    @param:JsonProperty("message") @field:JsonProperty("message") val message: String,
    @param:JsonProperty("user_id") @field:JsonProperty("user_id") val userId: String? = null,
)

data class ChatReply(
    @param:JsonProperty("reply") @field:JsonProperty("reply") val reply: String,
    @param:JsonProperty("needs_human") @field:JsonProperty("needs_human") val needsHuman: Boolean = false,
    @param:JsonProperty("category") @field:JsonProperty("category") val category: String? = null,
)

// ── content-moderation DTOs ────────────────────────────

/** 违规类别 — 与 Python 侧 ModerationCategory 小写值对齐。 */
enum class ModerationCategory {
    POLITICS, ABUSE, SPAM, PII, CLEAN;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(@JsonProperty value: String): ModerationCategory =
            entries.first { it.name.equals(value, ignoreCase = true) || it.name == value.uppercase().replace("-", "_") }
    }
}

enum class ModerationDecision {
    APPROVED, FLAGGED, BLOCKED;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(@JsonProperty value: String): ModerationDecision =
            entries.first { it.name.equals(value, ignoreCase = true) }
    }
}

enum class ContentSource {
    RATING, MERCHANT_INFO, PROFILE;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(@JsonProperty value: String): ContentSource =
            entries.first { it.name == value.uppercase().replace("-", "_") }
    }
}

data class ModerationRequest(
    @param:JsonProperty("content") @field:JsonProperty("content") val content: String,
    @param:JsonProperty("source") @field:JsonProperty("source") val source: ContentSource = ContentSource.RATING,
)

data class ModerationResult(
    @param:JsonProperty("decision") @field:JsonProperty("decision") val decision: ModerationDecision,
    @param:JsonProperty("category") @field:JsonProperty("category") val category: ModerationCategory,
    @param:JsonProperty("confidence") @field:JsonProperty("confidence") val confidence: Double,
    @param:JsonProperty("reason") @field:JsonProperty("reason") val reason: String,
)

// ── dispatch-optimizer DTOs ────────────────────────────

data class WorkerLocation(
    @param:JsonProperty("worker_id") @field:JsonProperty("worker_id") val workerId: String,
    @param:JsonProperty("lat") @field:JsonProperty("lat") val lat: Double,
    @param:JsonProperty("lng") @field:JsonProperty("lng") val lng: Double,
    @param:JsonProperty("active_order_count") @field:JsonProperty("active_order_count") val activeOrderCount: Int = 0,
)

data class DispatchRequest(
    @param:JsonProperty("pickup_lat") @field:JsonProperty("pickup_lat") val pickupLat: Double,
    @param:JsonProperty("pickup_lng") @field:JsonProperty("pickup_lng") val pickupLng: Double,
    @param:JsonProperty("candidates") @field:JsonProperty("candidates") val candidates: List<WorkerLocation>,
    @param:JsonProperty("max_distance_meters") @field:JsonProperty("max_distance_meters") val maxDistanceMeters: Int = 5_000,
)

data class DispatchSuggestion(
    @param:JsonProperty("worker_id") @field:JsonProperty("worker_id") val workerId: String,
    @param:JsonProperty("distance_meters") @field:JsonProperty("distance_meters") val distanceMeters: Double,
    @param:JsonProperty("score") @field:JsonProperty("score") val score: Double,
    @param:JsonProperty("reason") @field:JsonProperty("reason") val reason: String,
)

data class DispatchResult(
    @param:JsonProperty("suggestions") @field:JsonProperty("suggestions") val suggestions: List<DispatchSuggestion>,
    @param:JsonProperty("strategy") @field:JsonProperty("strategy") val strategy: String = "nearest_balanced",
)
