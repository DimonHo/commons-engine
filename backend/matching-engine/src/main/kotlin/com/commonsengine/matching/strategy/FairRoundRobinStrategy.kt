package com.commonsengine.matching.strategy

import com.commonsengine.platform.domain.MatchResult
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.Worker
import com.commonsengine.platform.geo.GeoUtils

/**
 * 策略二：公平轮转（Fair Round-Robin）
 *
 * 在合格候选中轮转分配，避免同一个劳动者一直接单。
 * 保护劳动者的公平接单权，防止算法偏好某些劳动者。
 */
class FairRoundRobinStrategy(
    private val config: AntiExploitationConfig = AntiExploitationConfig(),
    private val rotationCounter: MutableMap<String, Int> = mutableMapOf(),
) : MatchingStrategy {

    override val name = "fair-round-robin"

    override fun match(request: ServiceRequest, candidates: List<Worker>): MatchResult? {
        val eligible = candidates
            .filter { it.activeOrderCount < config.maxActiveOrders }
            .filter {
                GeoUtils.isWithinRadius(request.pickupLocation, it.currentLocation, config.maxMatchRadiusMeters)
            }
            .sortedBy { rotationCounter[it.id.value] ?: 0 }

        if (eligible.isEmpty()) return null

        // 在轮转序最少的候选中，取距离最近的
        val minCount = rotationCounter[eligible.first().id.value] ?: 0
        val leastServed = eligible.filter { (rotationCounter[it.id.value] ?: 0) == minCount }

        return leastServed
            .minByOrNull { GeoUtils.distance(request.pickupLocation, it.currentLocation) }
            ?.let { worker ->
                rotationCounter[worker.id.value] = (rotationCounter[worker.id.value] ?: 0) + 1
                val distance = GeoUtils.distance(request.pickupLocation, worker.currentLocation)

                MatchResult(
                    request = request,
                    worker = worker,
                    distanceMeters = distance,
                    strategy = name,
                    reason = buildString {
                        append("公平轮转：该劳动者本轮派单次数最少（${minCount}次）。")
                        append("距离：${String.format("%.0f", distance)}米。")
                        append("活跃订单：${worker.activeOrderCount}/${config.maxActiveOrders}。")
                    },
                )
            }
    }
}
