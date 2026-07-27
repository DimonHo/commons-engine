package com.commonsengine.dispatch.domain

import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.geo.GeoPoint
import java.time.Instant

/**
 * 劳动者工作偏好——劳动者自己设定边界，引擎尊重这些设定
 *
 * 这是公地引擎与资本平台的区别：
 * 资本平台的调度算法服务于平台效率最大化，
 * 公地引擎的调度服务于劳动者效率与福祉。
 */
data class WorkerPreferences(
    val workerId: String,
    val preferredServiceTypes: Set<ServiceType> = emptySet(),
    val preferredRegions: Set<String> = emptySet(),       // 偏好工作区域
    val excludedRegions: Set<String> = emptySet(),         // 不想去的区域
    val preferredTimeSlots: Set<TimeSlot> = emptySet(),    // 偏好工作时段
    val excludedTimeSlots: Set<TimeSlot> = emptySet(),     // 不想接的时段
    val maxConcurrentOrders: Int = 3,                      // 最大同时接单数
    val maxDailyHours: Double = 12.0,                      // 日最大工作时长
)

/**
 * 时间段
 */
data class TimeSlot(
    val dayOfWeek: Int,    // 1=周一 ... 7=周日
    val startHour: Int,    // 0-23
    val endHour: Int,      // 0-23
) {
    init {
        require(dayOfWeek in 1..7) { "dayOfWeek 必须在 1-7" }
        require(startHour in 0..23) { "startHour 必须在 0-23" }
        require(endHour in 0..23) { "endHour 必须在 0-23" }
    }
}

/**
 * 调度任务——匹配完成后，协调劳动者的行动路径和时间安排
 */
data class DispatchTask(
    val id: String,
    val workerId: String,
    val serviceType: ServiceType,
    val pickups: List<GeoPoint>,     // 取货点（外卖可能多取）
    val dropoffs: List<GeoPoint>,    // 送达点（可能多送）
    val estimatedDistanceMeters: Double = 0.0,
    val estimatedDurationMinutes: Int = 0,
    val assignedAt: Instant = Instant.now(),
    val deadline: Instant? = null,
)

/**
 * 调度建议——优化后的路径
 */
data class RouteSuggestion(
    val workerId: String,
    val orderedWaypoints: List<GeoPoint>,
    val totalDistanceMeters: Double,
    val estimatedTotalMinutes: Int,
    val reason: String,   // 为什么建议这条路线（可解释性）
)
