package com.commonsengine.dispatch.api

import com.commonsengine.dispatch.domain.DispatchTask
import com.commonsengine.dispatch.domain.TimeSlot
import com.commonsengine.dispatch.domain.WorkerPreferences
import com.commonsengine.dispatch.service.DispatchService
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.geo.GeoPoint
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 调度引擎 REST API
 *
 * 设计原则（架构文档 3.5 条）：
 * 1. 路径优化服务于劳动者效率，而非平台抽成最大化
 * 2. 劳动者可设定工作偏好和边界，引擎尊重这些设定
 */
@RestController
@RequestMapping("/api/v1/dispatch")
open class DispatchController(
    private val service: DispatchService,
) {

    /** 分配调度任务 */
    @PostMapping("/tasks")
    fun assignTask(@RequestBody body: DispatchTaskRequest): DispatchTaskResponse {
        val task = DispatchTask(
            id = body.id ?: UUID.randomUUID().toString(),
            workerId = body.workerId,
            serviceType = ServiceType.valueOf(body.serviceType),
            pickups = body.pickups.map { GeoPoint(it.lat, it.lng) },
            dropoffs = body.dropoffs.map { GeoPoint(it.lat, it.lng) },
            estimatedDistanceMeters = body.estimatedDistanceMeters ?: 0.0,
            estimatedDurationMinutes = body.estimatedDurationMinutes ?: 0,
            deadline = null,
        )
        val saved = service.assignTask(task)
        return saved.toResponse()
    }

    /** 查询调度任务 */
    @GetMapping("/tasks/{taskId}")
    fun findTask(@PathVariable taskId: String): DispatchTaskResponse? =
        service.findTask(taskId)?.toResponse()

    /** 查询某劳动者的所有调度任务 */
    @GetMapping("/workers/{workerId}/tasks")
    fun findTasksByWorker(@PathVariable workerId: String): List<DispatchTaskResponse> =
        service.findTasksByWorker(workerId).map { it.toResponse() }

    /** 保存或更新劳动者工作偏好 */
    @PostMapping("/workers/{workerId}/preferences")
    fun savePreferences(
        @PathVariable workerId: String,
        @RequestBody body: WorkerPreferencesRequest,
    ): Map<String, String> {
        val prefs = WorkerPreferences(
            workerId = workerId,
            preferredServiceTypes = body.preferredServiceTypes
                .mapNotNull { runCatching { ServiceType.valueOf(it) }.getOrNull() }.toSet(),
            preferredRegions = body.preferredRegions.toSet(),
            excludedRegions = body.excludedRegions.toSet(),
            preferredTimeSlots = body.preferredTimeSlots.map { it.toTimeSlot() }.toSet(),
            excludedTimeSlots = body.excludedTimeSlots.map { it.toTimeSlot() }.toSet(),
            maxConcurrentOrders = body.maxConcurrentOrders ?: 3,
            maxDailyHours = body.maxDailyHours ?: 12.0,
        )
        service.savePreferences(prefs)
        return mapOf("status" to "ok", "workerId" to workerId)
    }

    /** 查询劳动者工作偏好 */
    @GetMapping("/workers/{workerId}/preferences")
    fun findPreferences(@PathVariable workerId: String): WorkerPreferencesResponse? {
        val prefs = service.findPreferences(workerId) ?: return null
        return WorkerPreferencesResponse(
            workerId = prefs.workerId,
            preferredServiceTypes = prefs.preferredServiceTypes.map { it.name },
            preferredRegions = prefs.preferredRegions.toList(),
            excludedRegions = prefs.excludedRegions.toList(),
            preferredTimeSlots = prefs.preferredTimeSlots.map { TimeSlotDto(it.dayOfWeek, it.startHour, it.endHour) },
            excludedTimeSlots = prefs.excludedTimeSlots.map { TimeSlotDto(it.dayOfWeek, it.startHour, it.endHour) },
            maxConcurrentOrders = prefs.maxConcurrentOrders,
            maxDailyHours = prefs.maxDailyHours,
        )
    }

    /** 优化路径 */
    @PostMapping("/optimize-route")
    fun optimizeRoute(@RequestBody body: OptimizeRouteRequest): RouteSuggestionResponse {
        val task = DispatchTask(
            id = "optimize-${UUID.randomUUID()}",
            workerId = body.workerId,
            serviceType = ServiceType.valueOf(body.serviceType),
            pickups = body.pickups.map { GeoPoint(it.lat, it.lng) },
            dropoffs = body.dropoffs.map { GeoPoint(it.lat, it.lng) },
        )
        val currentLocation = GeoPoint(body.currentLat, body.currentLng)
        val route = service.optimizeRoute(currentLocation, task)
        return RouteSuggestionResponse(
            workerId = route.workerId,
            orderedWaypoints = route.orderedWaypoints.map { GeoPointDto(it.lat, it.lng) },
            totalDistanceMeters = route.totalDistanceMeters,
            estimatedTotalMinutes = route.estimatedTotalMinutes,
            reason = route.reason,
        )
    }
}

// ── DTO ──────────────────────────────────────────────────────────

data class GeoPointDto(val lat: Double, val lng: Double)

data class TimeSlotDto(val dayOfWeek: Int, val startHour: Int, val endHour: Int) {
    fun toTimeSlot() = TimeSlot(dayOfWeek, startHour, endHour)
}

data class DispatchTaskRequest(
    val id: String? = null,
    val workerId: String,
    val serviceType: String,
    val pickups: List<GeoPointDto>,
    val dropoffs: List<GeoPointDto>,
    val estimatedDistanceMeters: Double? = null,
    val estimatedDurationMinutes: Int? = null,
)

data class DispatchTaskResponse(
    val id: String,
    val workerId: String,
    val serviceType: String,
    val pickupCount: Int,
    val dropoffCount: Int,
    val estimatedDistanceMeters: Double,
    val estimatedDurationMinutes: Int,
    val assignedAt: String,
)

data class WorkerPreferencesRequest(
    val preferredServiceTypes: List<String> = emptyList(),
    val preferredRegions: List<String> = emptyList(),
    val excludedRegions: List<String> = emptyList(),
    val preferredTimeSlots: List<TimeSlotDto> = emptyList(),
    val excludedTimeSlots: List<TimeSlotDto> = emptyList(),
    val maxConcurrentOrders: Int? = null,
    val maxDailyHours: Double? = null,
)

data class WorkerPreferencesResponse(
    val workerId: String,
    val preferredServiceTypes: List<String>,
    val preferredRegions: List<String>,
    val excludedRegions: List<String>,
    val preferredTimeSlots: List<TimeSlotDto>,
    val excludedTimeSlots: List<TimeSlotDto>,
    val maxConcurrentOrders: Int,
    val maxDailyHours: Double,
)

data class OptimizeRouteRequest(
    val workerId: String,
    val serviceType: String,
    val currentLat: Double,
    val currentLng: Double,
    val pickups: List<GeoPointDto>,
    val dropoffs: List<GeoPointDto>,
)

data class RouteSuggestionResponse(
    val workerId: String,
    val orderedWaypoints: List<GeoPointDto>,
    val totalDistanceMeters: Double,
    val estimatedTotalMinutes: Int,
    val reason: String,
)

private fun DispatchTask.toResponse() = DispatchTaskResponse(
    id = id,
    workerId = workerId,
    serviceType = serviceType.name,
    pickupCount = pickups.size,
    dropoffCount = dropoffs.size,
    estimatedDistanceMeters = estimatedDistanceMeters,
    estimatedDurationMinutes = estimatedDurationMinutes,
    assignedAt = assignedAt.toString(),
)
