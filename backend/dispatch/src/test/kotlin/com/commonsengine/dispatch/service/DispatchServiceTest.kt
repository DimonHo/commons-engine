package com.commonsengine.dispatch.service

import com.commonsengine.dispatch.domain.DispatchTask
import com.commonsengine.dispatch.domain.WorkerPreferences
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DispatchServiceTest {

    private val service = DispatchService()

    @Test
    fun `optimize single pickup single dropoff`() {
        val worker = GeoPoint(39.9042, 116.4074)  // 天安门
        val task = DispatchTask(
            id = "t1",
            workerId = "w1",
            serviceType = ServiceType.FOOD_DELIVERY,
            pickups = listOf(GeoPoint(39.9100, 116.4100)),  // ~700m
            dropoffs = listOf(GeoPoint(39.9200, 116.4200)),  // 再 ~1.4km
        )

        val route = service.optimizeRoute(worker, task)

        assertEquals(2, route.orderedWaypoints.size)
        assertTrue(route.totalDistanceMeters > 0)
        assertTrue(route.estimatedTotalMinutes > 0)
        assertTrue(route.reason.contains("劳动者效率"))
    }

    @Test
    fun `optimize multi pickup orders by nearest first`() {
        val worker = GeoPoint(39.9042, 116.4074)
        val near = GeoPoint(39.9050, 116.4080)
        val mid = GeoPoint(39.9100, 116.4100)
        val far = GeoPoint(39.9200, 116.4200)

        val task = DispatchTask(
            id = "t2",
            workerId = "w1",
            serviceType = ServiceType.FOOD_DELIVERY,
            pickups = listOf(far, near, mid),  // 故意乱序
            dropoffs = emptyList(),
        )

        val route = service.optimizeRoute(worker, task)

        // 最近邻应先访问 near
        assertEquals(near, route.orderedWaypoints[0])
    }

    @Test
    fun `empty waypoints returns zero distance`() {
        val task = DispatchTask(
            id = "t3",
            workerId = "w1",
            serviceType = ServiceType.RIDE_HAILING,
            pickups = emptyList(),
            dropoffs = emptyList(),
        )

        val route = service.optimizeRoute(GeoPoint(39.0, 116.0), task)
        assertEquals(0.0, route.totalDistanceMeters)
        assertEquals(0, route.orderedWaypoints.size)
    }

    @Test
    fun `isAcceptableForWorker respects service type preference`() {
        val task = DispatchTask(
            id = "t4",
            workerId = "w1",
            serviceType = ServiceType.HOUSEKEEPING,
            pickups = listOf(GeoPoint(39.9, 116.4)),
            dropoffs = listOf(GeoPoint(39.91, 116.41)),
        )
        val prefs = WorkerPreferences(
            workerId = "w1",
            preferredServiceTypes = setOf(ServiceType.RIDE_HAILING),
        )

        assertFalse(service.isAcceptableForWorker(task, prefs))
    }

    @Test
    fun `isAcceptableForWorker accepts when no preference set`() {
        val task = DispatchTask(
            id = "t5",
            workerId = "w1",
            serviceType = ServiceType.RIDE_HAILING,
            pickups = listOf(GeoPoint(39.9, 116.4)),
            dropoffs = listOf(GeoPoint(39.91, 116.41)),
        )
        val prefs = WorkerPreferences(workerId = "w1")

        assertTrue(service.isAcceptableForWorker(task, prefs))
    }
}
