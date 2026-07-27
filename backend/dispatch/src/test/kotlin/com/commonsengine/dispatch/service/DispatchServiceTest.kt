package com.commonsengine.dispatch.service

import com.commonsengine.dispatch.domain.DispatchTask
import com.commonsengine.dispatch.domain.TimeSlot
import com.commonsengine.dispatch.domain.WorkerPreferences
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DispatchServiceTest {

    @Autowired
    private lateinit var service: DispatchService

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

    // ── 持久化测试 ─────────────────────────────────────

    @Test
    fun `assign task persists and can be found`() {
        val task = DispatchTask(
            id = "task-persist-1",
            workerId = "worker-persist-1",
            serviceType = ServiceType.FOOD_DELIVERY,
            pickups = listOf(GeoPoint(30.5728, 104.0668), GeoPoint(30.5828, 104.0768)),
            dropoffs = listOf(GeoPoint(30.5928, 104.0868)),
            estimatedDistanceMeters = 5200.0,
            estimatedDurationMinutes = 15,
        )

        service.assignTask(task)

        val found = service.findTask("task-persist-1")
        assertNotNull(found)
        assertEquals("worker-persist-1", found!!.workerId)
        assertEquals(ServiceType.FOOD_DELIVERY, found.serviceType)
        assertEquals(2, found.pickups.size)
        assertEquals(1, found.dropoffs.size)
        assertEquals(30.5728, found.pickups[0].lat, 0.0001)
        assertEquals(104.0668, found.pickups[0].lng, 0.0001)
        assertEquals(5200.0, found.estimatedDistanceMeters, 0.01)
    }

    @Test
    fun `find tasks by worker returns only that workers tasks`() {
        service.assignTask(DispatchTask(
            id = "task-w1-1", workerId = "worker-multi",
            serviceType = ServiceType.RIDE_HAILING,
            pickups = listOf(GeoPoint(30.0, 104.0)),
            dropoffs = listOf(GeoPoint(30.1, 104.1)),
        ))
        service.assignTask(DispatchTask(
            id = "task-w1-2", workerId = "worker-multi",
            serviceType = ServiceType.FOOD_DELIVERY,
            pickups = listOf(GeoPoint(30.2, 104.2)),
            dropoffs = listOf(GeoPoint(30.3, 104.3)),
        ))
        service.assignTask(DispatchTask(
            id = "task-w2-1", workerId = "worker-other",
            serviceType = ServiceType.RIDE_HAILING,
            pickups = listOf(GeoPoint(31.0, 105.0)),
            dropoffs = listOf(GeoPoint(31.1, 105.1)),
        ))

        val tasks = service.findTasksByWorker("worker-multi")
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.workerId == "worker-multi" })
    }

    @Test
    fun `find non-existent task returns null`() {
        val found = service.findTask("does-not-exist")
        assertNull(found)
    }

    @Test
    fun `save preferences persists and reloads all fields`() {
        val prefs = WorkerPreferences(
            workerId = "worker-prefs-1",
            preferredServiceTypes = setOf(ServiceType.RIDE_HAILING, ServiceType.FOOD_DELIVERY),
            preferredRegions = setOf("chengdu_wuhou", "chengdu_gaoxin"),
            excludedRegions = setOf("chengdu_jinjiang"),
            preferredTimeSlots = setOf(TimeSlot(1, 8, 12), TimeSlot(2, 8, 12)),
            excludedTimeSlots = setOf(TimeSlot(7, 0, 6)),
            maxConcurrentOrders = 2,
            maxDailyHours = 10.0,
        )

        service.savePreferences(prefs)

        val found = service.findPreferences("worker-prefs-1")
        assertNotNull(found)
        assertEquals(2, found!!.preferredServiceTypes.size)
        assertTrue(found.preferredServiceTypes.contains(ServiceType.RIDE_HAILING))
        assertEquals(2, found.preferredRegions.size)
        assertTrue(found.preferredRegions.contains("chengdu_wuhou"))
        assertEquals(1, found.excludedRegions.size)
        assertEquals(2, found.preferredTimeSlots.size)
        assertEquals(1, found.excludedTimeSlots.size)
        assertEquals(2, found.maxConcurrentOrders)
        assertEquals(10.0, found.maxDailyHours, 0.01)
    }

    @Test
    fun `save preferences updates existing record`() {
        val prefs = WorkerPreferences(
            workerId = "worker-prefs-update",
            maxConcurrentOrders = 3,
            maxDailyHours = 12.0,
        )
        service.savePreferences(prefs)

        // Update with new values
        val updated = WorkerPreferences(
            workerId = "worker-prefs-update",
            maxConcurrentOrders = 5,
            maxDailyHours = 8.0,
        )
        service.savePreferences(updated)

        val found = service.findPreferences("worker-prefs-update")
        assertNotNull(found)
        assertEquals(5, found!!.maxConcurrentOrders)
        assertEquals(8.0, found.maxDailyHours, 0.01)
    }

    @Test
    fun `find non-existent preferences returns null`() {
        val found = service.findPreferences("no-such-worker")
        assertNull(found)
    }

    @Test
    fun `geo points round trip preserves coordinates`() {
        val pickups = listOf(
            GeoPoint(30.5728, 104.0668),
            GeoPoint(30.1234, 104.5678),
            GeoPoint(29.9876, 103.4321),
        )
        val dropoffs = listOf(GeoPoint(30.9999, 104.9999))

        val task = DispatchTask(
            id = "task-geo-1",
            workerId = "worker-geo",
            serviceType = ServiceType.ERRAND,
            pickups = pickups,
            dropoffs = dropoffs,
        )
        service.assignTask(task)

        val found = service.findTask("task-geo-1")!!
        assertEquals(3, found.pickups.size)
        assertEquals(1, found.dropoffs.size)
        assertEquals(30.5728, found.pickups[0].lat, 0.00001)
        assertEquals(104.0668, found.pickups[0].lng, 0.00001)
        assertEquals(30.9999, found.dropoffs[0].lat, 0.00001)
        assertEquals(104.9999, found.dropoffs[0].lng, 0.00001)
    }

    @Test
    fun `task with deadline persists`() {
        val deadline = java.time.Instant.now().plusSeconds(3600)
        val task = DispatchTask(
            id = "task-deadline-1",
            workerId = "worker-deadline",
            serviceType = ServiceType.RIDE_HAILING,
            pickups = listOf(GeoPoint(30.0, 104.0)),
            dropoffs = listOf(GeoPoint(30.1, 104.1)),
            deadline = deadline,
        )
        service.assignTask(task)

        val found = service.findTask("task-deadline-1")!!
        assertNotNull(found.deadline)
        assertEquals(deadline.epochSecond, found.deadline!!.epochSecond)
    }
}
