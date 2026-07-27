package com.commonsengine.matching.strategy

import com.commonsengine.platform.domain.ConsumerId
import com.commonsengine.platform.domain.RequestId
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.domain.Worker
import com.commonsengine.platform.domain.WorkerId
import com.commonsengine.platform.geo.GeoPoint
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FairRoundRobinStrategyTest {

    private val pickup = GeoPoint(39.9042, 116.4074)

    private fun request() = ServiceRequest(
        id = RequestId.random(),
        consumerId = ConsumerId("c1"),
        type = ServiceType.RIDE_HAILING,
        pickupLocation = pickup,
    )

    private fun worker(id: String, lat: Double = 39.9050, lng: Double = 116.4080) = Worker(
        id = WorkerId(id),
        name = "worker-$id",
        currentLocation = GeoPoint(lat, lng),
    )

    @Test
    fun `rotates between equally distant workers`() {
        val strategy = FairRoundRobinStrategy()

        // 三个等距劳动者
        val candidates = listOf(
            worker("A", 39.9050, 116.4080),
            worker("B", 39.9050, 116.4080),
            worker("C", 39.9050, 116.4080),
        )

        // 第一次匹配 → A（距离相同，取列表第一个）
        val r1 = strategy.match(request(), candidates)
        assertNotNull(r1)

        // 第二次 → B（A 已被轮转过一次）
        val r2 = strategy.match(request(), candidates)
        assertNotNull(r2)
        assertTrue(r2.worker.id.value != r1.worker.id.value, "第二次匹配应轮转到不同劳动者")

        // 第三次 → C（A、B 都被轮转过）
        val r3 = strategy.match(request(), candidates)
        assertNotNull(r3)
        assertTrue(r3.worker.id.value != r1.worker.id.value && r3.worker.id.value != r2.worker.id.value,
            "第三次匹配应轮转到第三个劳动者")

        // 第四次 → 回到 A（轮回完成）
        val r4 = strategy.match(request(), candidates)
        assertNotNull(r4)
        assertEquals(r1.worker.id.value, r4.worker.id.value, "第四次应轮回回第一个")
    }

    @Test
    fun `reason includes rotation count`() {
        val strategy = FairRoundRobinStrategy()
        val candidates = listOf(worker("A"), worker("B"))

        val result = strategy.match(request(), candidates)
        assertNotNull(result)
        assertTrue(result.reason.contains("公平轮转"), "理由应包含'公平轮转'，实际: ${result.reason}")
        assertTrue(result.reason.contains("0次"), "首次轮转次数应为0")
    }

    @Test
    fun `returns null when no eligible candidates`() {
        val strategy = FairRoundRobinStrategy(
            AntiExploitationConfig(maxActiveOrders = 0),
        )
        // 所有劳动者都已超载
        val candidates = listOf(
            Worker(
                id = WorkerId("overloaded"),
                name = "busy",
                currentLocation = GeoPoint(39.9050, 116.4080),
                activeOrderCount = 1,
            ),
        )

        val result = strategy.match(request(), candidates)
        assertTrue(result == null, "无合格候选应返回 null")
    }
}
