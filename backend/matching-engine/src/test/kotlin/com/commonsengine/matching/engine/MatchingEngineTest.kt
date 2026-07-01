package com.commonsengine.matching.engine

import com.commonsengine.platform.domain.ConsumerId
import com.commonsengine.platform.domain.RequestId
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.domain.Worker
import com.commonsengine.platform.domain.WorkerId
import com.commonsengine.platform.geo.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MatchingEngineTest {

    private val pickup = GeoPoint(39.9042, 116.4074)

    private fun request() = ServiceRequest(
        id = RequestId.random(),
        consumerId = ConsumerId("c1"),
        type = ServiceType.RIDE_HAILING,
        pickupLocation = pickup,
    )

    private fun worker(id: String, lat: Double, lng: Double) = Worker(
        id = WorkerId(id),
        name = "w-$id",
        currentLocation = GeoPoint(lat, lng),
    )

    @Test
    fun `default strategy is nearest-first`() {
        val engine = MatchingEngine()
        assertEquals("nearest-first", engine.currentStrategy())
    }

    @Test
    fun `can switch strategy at runtime`() {
        val engine = MatchingEngine()
        engine.setStrategy("fair-round-robin")
        assertEquals("fair-round-robin", engine.currentStrategy())
    }

    @Test
    fun `rejects unknown strategy`() {
        val engine = MatchingEngine()
        var threwError = false
        try {
            engine.setStrategy("greedy-profit-max")
        } catch (e: IllegalArgumentException) {
            threwError = true
        }
        assert(threwError)
    }

    @Test
    fun `match returns result with strategy name`() {
        val engine = MatchingEngine()
        val candidates = listOf(
            worker("near", 39.9050, 116.4080),
            worker("far", 39.9200, 116.4200),
        )

        val result = engine.match(request(), candidates)
        assertNotNull(result)
        assertEquals("nearest-first", result.strategy)
        assertEquals("near", result.worker.id.value)
    }

    @Test
    fun `match returns null for empty candidates`() {
        val engine = MatchingEngine()
        val result = engine.match(request(), emptyList())
        assertNull(result)
    }

    @Test
    fun `available strategies are listed`() {
        val engine = MatchingEngine()
        val strategies = engine.availableStrategies()
        assert("nearest-first" in strategies)
        assert("fair-round-robin" in strategies)
    }
}
