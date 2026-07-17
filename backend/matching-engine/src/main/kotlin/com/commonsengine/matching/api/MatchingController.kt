package com.commonsengine.matching.api

import com.commonsengine.matching.engine.MatchingEngine
import com.commonsengine.matching.service.WorkerLocationService
import com.commonsengine.platform.domain.ConsumerId
import com.commonsengine.platform.domain.RequestId
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.domain.Worker
import com.commonsengine.platform.domain.WorkerId
import com.commonsengine.platform.geo.GeoPoint
import com.commonsengine.platform.support.Enums
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 匹配引擎 REST API
 */
@RestController
@RequestMapping("/api/v1/matching")
open class MatchingController(
    private val engine: MatchingEngine,
    private val locationService: WorkerLocationService,
) {

    /** 健康检查 + 当前策略 */
    @GetMapping("/health")
    fun health(): Map<String, Any> = mapOf(
        "status" to "UP",
        "currentStrategy" to engine.currentStrategy(),
        "availableStrategies" to engine.availableStrategies(),
    )

    /** 切换匹配策略 */
    @PostMapping("/strategy")
    fun setStrategy(@Valid @RequestBody body: StrategyRequest): Map<String, String> {
        engine.setStrategy(body.strategy)
        return mapOf("status" to "ok", "currentStrategy" to engine.currentStrategy())
    }

    /**
     * 手动匹配——调用方提供候选列表（兼容旧接口）
     */
    @PostMapping("/match")
    fun match(@Valid @RequestBody body: MatchRequest): MatchResponse {
        val request = toServiceRequest(body)
        val candidates = body.candidates.map { it.toWorker() }
        return doMatch(request, candidates)
    }

    /**
     * 自动匹配——系统从数据库检索附近劳动者（#37 PostGIS 地理索引）
     *
     * 调用方只需提供位置和服务类型，系统自动查找 radiusMeters 范围内的劳动者。
     */
    @PostMapping("/match/auto")
    fun autoMatch(@Valid @RequestBody body: AutoMatchRequest): MatchResponse {
        val request = ServiceRequest(
            id = RequestId.random(),
            consumerId = ConsumerId(body.consumerId),
            type = Enums.parse<ServiceType>(body.serviceType),
            pickupLocation = GeoPoint(body.pickupLat, body.pickupLng),
        )

        val candidates = locationService.findNearbyWorkers(
            center = request.pickupLocation,
            radiusMeters = body.radiusMeters ?: 5000.0,
            maxActiveOrders = body.maxActiveOrders ?: 3,
        )

        return doMatch(request, candidates)
    }

    /**
     * 劳动者上报位置（心跳）
     */
    @PostMapping("/workers/{workerId}/location")
    fun updateLocation(
        @PathVariable workerId: String,
        @Valid @RequestBody body: LocationUpdate,
    ): Map<String, String> {
        locationService.upsertLocation(
            workerId = workerId,
            name = body.name,
            lat = body.lat,
            lng = body.lng,
            serviceTypes = body.serviceTypes?.joinToString(",") ?: "",
            rating = body.rating ?: 5.0,
            activeOrderCount = body.activeOrderCount ?: 0,
        )
        return mapOf("status" to "ok")
    }

    // ── 内部方法 ──────────────────────────────────────

    private fun toServiceRequest(body: MatchRequest) = ServiceRequest(
        id = RequestId.random(),
        consumerId = ConsumerId(body.consumerId),
        type = Enums.parse<ServiceType>(body.serviceType),
        pickupLocation = GeoPoint(body.pickupLat, body.pickupLng),
    )

    private fun doMatch(request: ServiceRequest, candidates: List<Worker>): MatchResponse {
        val result = engine.match(request, candidates)
            ?: return MatchResponse(
                matched = false,
                reason = "无合格候选劳动者（候选数: ${candidates.size}）",
                strategy = engine.currentStrategy(),
            )

        return MatchResponse(
            matched = true,
            workerId = result.worker.id.value,
            workerName = result.worker.name,
            distanceMeters = result.distanceMeters,
            strategy = result.strategy,
            reason = result.reason,
        )
    }
}

// ── DTO ──────────────────────────────────────────────────────────

data class StrategyRequest(@field:NotBlank val strategy: String)

data class MatchRequest(
    @field:NotBlank val consumerId: String,
    @field:NotBlank val serviceType: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val candidates: List<CandidateDto>,
)

data class AutoMatchRequest(
    @field:NotBlank val consumerId: String,
    @field:NotBlank val serviceType: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val radiusMeters: Double? = null,
    val maxActiveOrders: Int? = null,
)

data class CandidateDto(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val rating: Double = 5.0,
    val activeOrderCount: Int = 0,
) {
    fun toWorker() = Worker(
        id = WorkerId(id),
        name = name,
        currentLocation = GeoPoint(lat, lng),
        rating = rating,
        activeOrderCount = activeOrderCount,
    )
}

data class LocationUpdate(
    @field:NotBlank val name: String,
    val lat: Double,
    val lng: Double,
    val serviceTypes: List<String>? = null,
    val rating: Double? = null,
    val activeOrderCount: Int? = null,
)

data class MatchResponse(
    val matched: Boolean,
    val workerId: String? = null,
    val workerName: String? = null,
    val distanceMeters: Double? = null,
    val strategy: String,
    val reason: String,
)
