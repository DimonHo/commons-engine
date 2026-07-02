package com.commonsengine.matching.service

import com.commonsengine.platform.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkerLocationServiceTest {

    @Autowired
    private lateinit var service: WorkerLocationService

    // 北京中关村附近
    private val zgc = GeoPoint(39.9847, 116.3076)

    @BeforeEach
    fun setup() {
        // 劳动者 A：中关村 100 米内
        service.upsertLocation("w-A", "骑手A", 39.9850, 116.3080, "FOOD_DELIVERY")
        // 劳动者 B：中关村 500 米内
        service.upsertLocation("w-B", "骑手B", 39.9820, 116.3050, "FOOD_DELIVERY", rating = 4.8)
        // 劳动者 C：5 公里外（不应出现在 1km 搜索结果中）
        service.upsertLocation("w-C", "骑手C", 40.0200, 116.3500, "FOOD_DELIVERY")
        // 劳动者 D：满单（不应出现在结果中）
        service.upsertLocation("w-D", "骑手D", 39.9848, 116.3078, "FOOD_DELIVERY", activeOrderCount = 3)
    }

    @Test
    fun `findNearbyWorkers returns workers within radius`() {
        val nearby = service.findNearbyWorkers(zgc, radiusMeters = 1000.0)

        // A 和 B 在 1km 内，C 在 5km 外，D 满单
        assertEquals(2, nearby.size)
        assertTrue(nearby.any { it.id.value == "w-A" })
        assertTrue(nearby.any { it.id.value == "w-B" })
    }

    @Test
    fun `findNearbyWorkers excludes workers at max capacity`() {
        val nearby = service.findNearbyWorkers(zgc, radiusMeters = 1000.0, maxActiveOrders = 3)
        // D 有 3 个活跃订单，等于 maxActiveOrders，应被排除
        assertTrue(nearby.none { it.id.value == "w-D" })
    }

    @Test
    fun `findNearbyWorkers returns empty when no one in range`() {
        // 北京天安门（距中关村约 10km）
        val tam = GeoPoint(39.9087, 116.3974)
        val nearby = service.findNearbyWorkers(tam, radiusMeters = 1000.0)
        assertTrue(nearby.isEmpty())
    }

    @Test
    fun `findNearbyWorkers sorts by distance ascending`() {
        val nearby = service.findNearbyWorkers(zgc, radiusMeters = 1000.0)
        assertEquals(2, nearby.size)
        // A 更近（~100m），B 更远（~500m）
        assertEquals("w-A", nearby[0].id.value)
    }

    @Test
    fun `upsertLocation updates existing worker`() {
        service.upsertLocation("w-A", "骑手A-改名", 39.9850, 116.3080, "FOOD_DELIVERY")

        val nearby = service.findNearbyWorkers(zgc, radiusMeters = 500.0)
        val worker = nearby.find { it.id.value == "w-A" }
        assertTrue(worker != null)
        assertEquals("骑手A-改名", worker!!.name)
    }
}
