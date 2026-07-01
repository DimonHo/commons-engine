package com.commonsengine.matching.strategy

import com.commonsengine.platform.domain.ConsumerId
import com.commonsengine.platform.domain.RequestId
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.domain.Worker
import com.commonsengine.platform.domain.WorkerId
import com.commonsengine.platform.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NearestFirstStrategyTest {

    private val pickup = GeoPoint(39.9042, 116.4074) // 北京天安门

    private fun request() = ServiceRequest(
        id = RequestId.random(),
        consumerId = ConsumerId("c1"),
        type = ServiceType.RIDE_HAILING,
        pickupLocation = pickup,
    )

    private fun worker(id: String, lat: Double, lng: Double, orders: Int = 0, rating: Double = 5.0) = Worker(
        id = WorkerId(id),
        name = "worker-$id",
        currentLocation = GeoPoint(lat, lng),
        rating = rating,
        activeOrderCount = orders,
    )

    @Test
    fun `selects nearest worker`() {
        val strategy = NearestFirstStrategy()
        val candidates = listOf(
            worker("far", 39.9200, 116.4200),   // ~2km
            worker("near", 39.9050, 116.4080),  // ~100m
            worker("mid", 39.9100, 116.4100),   // ~700m
        )

        val result = strategy.match(request(), candidates)

        assertNotNull(result)
        assertEquals("near", result.worker.id.value)
        assertTrue(result.distanceMeters < 200, "最近的劳动者应在 200 米内，实际 ${result.distanceMeters}")
        assertTrue(result.reason.contains("距离最近"), "理由应包含'距离最近'")
    }

    @Test
    fun `returns null when no candidates within radius`() {
        val strategy = NearestFirstStrategy(
            AntiExploitationConfig(maxMatchRadiusMeters = 100.0),
        )
        val candidates = listOf(
            worker("far", 40.0000, 116.5000),  // 很远
        )

        val result = strategy.match(request(), candidates)
        assertNull(result)
    }

    @Test
    fun `filters out overloaded workers`() {
        val strategy = NearestFirstStrategy(
            AntiExploitationConfig(maxActiveOrders = 2),
        )
        val candidates = listOf(
            worker("overloaded", 39.9050, 116.4080, orders = 3),  // 很近但超载
            worker("available", 39.9100, 116.4100, orders = 0),   // 稍远但可用
        )

        val result = strategy.match(request(), candidates)

        assertNotNull(result)
        assertEquals("available", result.worker.id.value)
        assertTrue(result.reason.contains("活跃订单数"))
    }

    @Test
    fun `empty candidate list returns null`() {
        val strategy = NearestFirstStrategy()
        val result = strategy.match(request(), emptyList())
        assertNull(result)
    }
}
