package com.commonsengine.platform.domain

import com.commonsengine.platform.geo.GeoPoint
import java.time.Instant
import java.util.UUID

/**
 * 劳动者（骑手/司机/家政工等）
 */
data class Worker(
    val id: WorkerId,
    val name: String,
    val currentLocation: GeoPoint,
    val rating: Double = 5.0,
    val activeOrderCount: Int = 0,
    val preferredServiceTypes: Set<ServiceType> = emptySet(),
)

@JvmInline
value class WorkerId(val value: String) {
    companion object {
        fun random() = WorkerId(UUID.randomUUID().toString())
    }
}

/**
 * 消费者（用户）
 */
data class Consumer(
    val id: ConsumerId,
    val name: String,
    val currentLocation: GeoPoint,
)

@JvmInline
value class ConsumerId(val value: String)

/**
 * 服务请求（乘客叫车、用户点餐）
 */
data class ServiceRequest(
    val id: RequestId,
    val consumerId: ConsumerId,
    val type: ServiceType,
    val pickupLocation: GeoPoint,
    val destination: GeoPoint? = null,
    val createdAt: Instant = Instant.now(),
)

@JvmInline
value class RequestId(val value: String) {
    companion object {
        fun random() = RequestId(UUID.randomUUID().toString())
    }
}

/**
 * 服务类型
 */
enum class ServiceType {
    RIDE_HAILING,    // 网约车
    FOOD_DELIVERY,   // 外卖
    HOUSEKEEPING,    // 家政
    ERRAND,          // 跑腿
}

/**
 * 匹配结果
 */
data class MatchResult(
    val request: ServiceRequest,
    val worker: Worker,
    val distanceMeters: Double,
    val strategy: String,
    val reason: String,    // 可解释性：为什么匹配给这个劳动者
)
