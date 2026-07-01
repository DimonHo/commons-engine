package com.commonsengine.matching.api

import com.commonsengine.matching.engine.MatchingEngine
import com.commonsengine.platform.domain.ConsumerId
import com.commonsengine.platform.domain.RequestId
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.domain.Worker
import com.commonsengine.platform.domain.WorkerId
import com.commonsengine.platform.geo.GeoPoint
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 匹配引擎 REST API
 */
@RestController
@RequestMapping("/api/v1/matching")
class MatchingController(private val engine: MatchingEngine) {

    /** 健康检查 + 当前策略 */
    @GetMapping("/health")
    fun health(): Map<String, Any> = mapOf(
        "status" to "UP",
        "currentStrategy" to engine.currentStrategy(),
        "availableStrategies" to engine.availableStrategies(),
    )

    /** 切换匹配策略 */
    @PostMapping("/strategy")
    fun setStrategy(@RequestBody body: StrategyRequest): Map<String, String> {
        engine.setStrategy(body.strategy)
        return mapOf("status" to "ok", "currentStrategy" to engine.currentStrategy())
    }

    /** 执行匹配 */
    @PostMapping("/match")
    fun match(@RequestBody body: MatchRequest): MatchResponse {
        val request = ServiceRequest(
            id = RequestId.random(),
            consumerId = ConsumerId(body.consumerId),
            type = ServiceType.valueOf(body.serviceType),
            pickupLocation = GeoPoint(body.pickupLat, body.pickupLng),
        )

        val candidates = body.candidates.map { w ->
            Worker(
                id = WorkerId(w.id),
                name = w.name,
                currentLocation = GeoPoint(w.lat, w.lng),
                rating = w.rating,
                activeOrderCount = w.activeOrderCount,
            )
        }

        val result = engine.match(request, candidates)
            ?: return MatchResponse(matched = false, reason = "无合格候选劳动者", strategy = engine.currentStrategy())

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

data class StrategyRequest(val strategy: String)

data class MatchRequest(
    val consumerId: String,
    val serviceType: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val candidates: List<CandidateDto>,
)

data class CandidateDto(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val rating: Double = 5.0,
    val activeOrderCount: Int = 0,
)

data class MatchResponse(
    val matched: Boolean,
    val workerId: String? = null,
    val workerName: String? = null,
    val distanceMeters: Double? = null,
    val strategy: String,
    val reason: String,
)
