package com.commonsengine.dispatch.infrastructure.persistence

import com.commonsengine.dispatch.domain.DispatchTask
import com.commonsengine.dispatch.domain.TimeSlot
import com.commonsengine.dispatch.domain.WorkerPreferences
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.geo.GeoPoint
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

private val mapper = jacksonObjectMapper()

// ── GeoPoint JSON 序列化辅助 ──────────────────────────

private fun geoPointsToJson(points: List<GeoPoint>): String =
    mapper.writeValueAsString(points.map { mapOf("lat" to it.lat, "lng" to it.lng) })

private fun jsonToGeoPoints(json: String): List<GeoPoint> {
    if (json.isBlank()) return emptyList()
    val type = object : TypeReference<List<Map<String, Double>>>() {}
    return mapper.readValue(json, type).map { GeoPoint(it["lat"]!!, it["lng"]!!) }
}

// ── Enum Set JSON 序列化辅助 ──────────────────────────

private fun enumSetToJson(types: Set<ServiceType>): String =
    if (types.isEmpty()) "" else mapper.writeValueAsString(types.map { it.name })

private fun jsonToServiceTypeSet(json: String?): Set<ServiceType> {
    if (json.isNullOrBlank()) return emptySet()
    val type = object : TypeReference<List<String>>() {}
    return mapper.readValue(json, type).mapNotNull { runCatching { ServiceType.valueOf(it) }.getOrNull() }.toSet()
}

// ── String Set JSON 序列化辅助 ────────────────────────

private fun stringSetToJson(set: Set<String>): String =
    if (set.isEmpty()) "" else mapper.writeValueAsString(set)

private fun jsonToStringSet(json: String?): Set<String> {
    if (json.isNullOrBlank()) return emptySet()
    val type = object : TypeReference<List<String>>() {}
    return mapper.readValue(json, type).toSet()
}

// ── TimeSlot Set JSON 序列化辅助 ──────────────────────

private fun timeSlotsToJson(slots: Set<TimeSlot>): String =
    if (slots.isEmpty()) "" else mapper.writeValueAsString(slots.map {
        mapOf("dayOfWeek" to it.dayOfWeek, "startHour" to it.startHour, "endHour" to it.endHour)
    })

private fun jsonToTimeSlots(json: String?): Set<TimeSlot> {
    if (json.isNullOrBlank()) return emptySet()
    val type = object : TypeReference<List<Map<String, Int>>>() {}
    return mapper.readValue(json, type).map {
        TimeSlot(it["dayOfWeek"]!!, it["startHour"]!!, it["endHour"]!!)
    }.toSet()
}

// ══════════════════════════════════════════════════════
// DispatchTaskEntity
// ══════════════════════════════════════════════════════

/**
 * 调度任务 JPA 实体
 *
 * pickups/dropoffs: List<GeoPoint> 以 JSON 字符串存储。
 */
@Entity
@Table(name = "dispatch_tasks")
class DispatchTaskEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "task_id", nullable = false, length = 36)
    val taskId: String,

    @Column(name = "worker_id", nullable = false, length = 36)
    val workerId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 30)
    val serviceType: ServiceType,

    /** JSON: [{"lat":..,"lng":..}] */
    @Column(name = "pickups", nullable = false, columnDefinition = "TEXT")
    val pickups: String,

    /** JSON: [{"lat":..,"lng":..}] */
    @Column(name = "dropoffs", nullable = false, columnDefinition = "TEXT")
    val dropoffs: String,

    @Column(name = "estimated_distance_meters", nullable = false)
    val estimatedDistanceMeters: Double = 0.0,

    @Column(name = "estimated_duration_minutes", nullable = false)
    val estimatedDurationMinutes: Int = 0,

    @Column(name = "assigned_at", nullable = false)
    val assignedAt: Instant,

    @Column(name = "deadline")
    val deadline: Instant? = null,
)

// ── DispatchTask Entity ↔ Domain ──────────────────────

fun DispatchTaskEntity.toDomain(): DispatchTask = DispatchTask(
    id = taskId,
    workerId = workerId,
    serviceType = serviceType,
    pickups = jsonToGeoPoints(pickups),
    dropoffs = jsonToGeoPoints(dropoffs),
    estimatedDistanceMeters = estimatedDistanceMeters,
    estimatedDurationMinutes = estimatedDurationMinutes,
    assignedAt = assignedAt,
    deadline = deadline,
)

fun DispatchTask.toEntity(): DispatchTaskEntity = DispatchTaskEntity(
    taskId = id,
    workerId = workerId,
    serviceType = serviceType,
    pickups = geoPointsToJson(pickups),
    dropoffs = geoPointsToJson(dropoffs),
    estimatedDistanceMeters = estimatedDistanceMeters,
    estimatedDurationMinutes = estimatedDurationMinutes,
    assignedAt = assignedAt,
    deadline = deadline,
)

// ══════════════════════════════════════════════════════
// WorkerPreferencesEntity
// ══════════════════════════════════════════════════════

/**
 * 劳动者工作偏好 JPA 实体
 *
 * 反榨取设计核心数据：劳动者自己设定的工作边界，引擎必须尊重。
 * 所有 Set 类型以 JSON 字符串存储。
 */
@Entity
@Table(name = "worker_preferences")
class WorkerPreferencesEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "worker_id", nullable = false, length = 36)
    val workerId: String,

    /** JSON: ["RIDE_HAILING","FOOD_DELIVERY"] */
    @Column(name = "preferred_service_types", columnDefinition = "TEXT")
    val preferredServiceTypes: String? = null,

    /** JSON: ["chengdu_wuhou", ...] */
    @Column(name = "preferred_regions", columnDefinition = "TEXT")
    val preferredRegions: String? = null,

    /** JSON: ["chengdu_jinjiang", ...] */
    @Column(name = "excluded_regions", columnDefinition = "TEXT")
    val excludedRegions: String? = null,

    /** JSON: [{"dayOfWeek":1,"startHour":8,"endHour":12}, ...] */
    @Column(name = "preferred_time_slots", columnDefinition = "TEXT")
    val preferredTimeSlots: String? = null,

    /** JSON: [{"dayOfWeek":7,"startHour":0,"endHour":6}, ...] */
    @Column(name = "excluded_time_slots", columnDefinition = "TEXT")
    val excludedTimeSlots: String? = null,

    @Column(name = "max_concurrent_orders", nullable = false)
    val maxConcurrentOrders: Int = 3,

    @Column(name = "max_daily_hours", nullable = false)
    val maxDailyHours: Double = 12.0,
)

// ── WorkerPreferences Entity ↔ Domain ─────────────────

fun WorkerPreferencesEntity.toDomain(): WorkerPreferences = WorkerPreferences(
    workerId = workerId,
    preferredServiceTypes = jsonToServiceTypeSet(preferredServiceTypes),
    preferredRegions = jsonToStringSet(preferredRegions),
    excludedRegions = jsonToStringSet(excludedRegions),
    preferredTimeSlots = jsonToTimeSlots(preferredTimeSlots),
    excludedTimeSlots = jsonToTimeSlots(excludedTimeSlots),
    maxConcurrentOrders = maxConcurrentOrders,
    maxDailyHours = maxDailyHours,
)

fun WorkerPreferences.toEntity(): WorkerPreferencesEntity = WorkerPreferencesEntity(
    workerId = workerId,
    preferredServiceTypes = enumSetToJson(preferredServiceTypes),
    preferredRegions = stringSetToJson(preferredRegions),
    excludedRegions = stringSetToJson(excludedRegions),
    preferredTimeSlots = timeSlotsToJson(preferredTimeSlots),
    excludedTimeSlots = timeSlotsToJson(excludedTimeSlots),
    maxConcurrentOrders = maxConcurrentOrders,
    maxDailyHours = maxDailyHours,
)
