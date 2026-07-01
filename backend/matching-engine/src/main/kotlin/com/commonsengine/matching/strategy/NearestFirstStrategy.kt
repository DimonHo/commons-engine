package com.commonsengine.matching.strategy

import com.commonsengine.platform.domain.MatchResult
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.Worker
import com.commonsengine.platform.geo.GeoUtils

/**
 * 策略一：距离优先（Nearest First）
 *
 * 选择距离乘客/取餐点最近的空闲劳动者。
 * 最直观的策略，适合初始 MVP。
 */
class NearestFirstStrategy(
    private val config: AntiExploitationConfig = AntiExploitationConfig(),
) : MatchingStrategy {

    override val name = "nearest-first"

    override fun match(request: ServiceRequest, candidates: List<Worker>): MatchResult? {
        val eligible = candidates.filter { worker ->
            worker.activeOrderCount < config.maxActiveOrders &&
                GeoUtils.isWithinRadius(
                    center = request.pickupLocation,
                    point = worker.currentLocation,
                    radiusMeters = config.maxMatchRadiusMeters,
                )
        }

        if (eligible.isEmpty()) return null

        return eligible
            .minByOrNull { GeoUtils.distance(request.pickupLocation, it.currentLocation) }
            ?.let { nearest ->
                val distance = GeoUtils.distance(request.pickupLocation, nearest.currentLocation)
                MatchResult(
                    request = request,
                    worker = nearest,
                    distanceMeters = distance,
                    strategy = name,
                    reason = buildString {
                        append("距离最近：${String.format("%.0f", distance)}米。")
                        append("当前活跃订单数：${nearest.activeOrderCount}/${config.maxActiveOrders}。")
                        append("劳动者评分：${String.format("%.1f", nearest.rating)}。")
                    },
                )
            }
    }
}
