package com.commonsengine.platform.e2e

import com.commonsengine.dispatch.domain.DispatchTask
import com.commonsengine.dispatch.domain.WorkerPreferences
import com.commonsengine.dispatch.service.DispatchService
import com.commonsengine.identity.domain.MemberRole
import com.commonsengine.identity.service.MembershipService
import com.commonsengine.matching.engine.MatchingEngine
import com.commonsengine.matching.service.WorkerLocationService
import com.commonsengine.payment.domain.SettlementRule
import com.commonsengine.payment.domain.Transaction
import com.commonsengine.payment.domain.TransactionId
import com.commonsengine.payment.service.PaymentService
import com.commonsengine.platform.domain.ConsumerId
import com.commonsengine.platform.domain.RequestId
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.platform.domain.WorkerId
import com.commonsengine.platform.geo.GeoPoint
import com.commonsengine.rating.domain.Rating
import com.commonsengine.rating.domain.RatingDirection
import com.commonsengine.rating.domain.RatingId
import com.commonsengine.rating.domain.RatingTag
import com.commonsengine.rating.service.RatingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 端到端全链路集成测试 (#40)
 *
 * 验证公地引擎完整业务流程：
 * 注册 → 位置上报 → 匹配 → 派单 → 分账 → 评价
 *
 * 这条链路是阶段1 的验收标准——证明五大模块能协同工作。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EndToEndFlowTest {

    @Autowired private lateinit var membershipService: MembershipService
    @Autowired private lateinit var workerLocationService: WorkerLocationService
    @Autowired private lateinit var matchingEngine: MatchingEngine
    @Autowired private lateinit var dispatchService: DispatchService
    @Autowired private lateinit var paymentService: PaymentService
    @Autowired private lateinit var ratingService: RatingService

    @Test
    fun `full flow - consumer requests ride, worker matched, dispatched, paid, rated`() {
        // ━━━━━━ 1. 注册：消费者 + 劳动者 ━━━━━━
        val consumer = membershipService.register(
            name = "乘客小明",
            phone = "13800001111",
            roles = setOf(MemberRole.CONSUMER),
        )
        val worker = membershipService.register(
            name = "司机老王",
            phone = "13800002222",
            roles = setOf(MemberRole.WORKER),
        )
        assertNotNull(consumer.id.value)
        assertNotNull(worker.id.value)
        assertEquals(MemberRole.WORKER, worker.roles.first())

        // ━━━━━━ 2. 劳动者上报位置 ━━━━━━
        // 北京三里屯附近
        val sanlitun = GeoPoint(39.9332, 116.4543)
        workerLocationService.upsertLocation(
            workerId = worker.id.value,
            name = worker.name,
            lat = sanlitun.lat + 0.001,  // 劳动者在消费者 100 米外
            lng = sanlitun.lng + 0.001,
            serviceTypes = "RIDE_HAILING",
            rating = 4.9,
            activeOrderCount = 0,
        )

        // ━━━━━━ 3. 匹配：消费者叫车 ━━━━━━
        val candidates = workerLocationService.findNearbyWorkers(
            center = sanlitun,
            radiusMeters = 1000.0,
            maxActiveOrders = 3,
        )
        assertTrue(candidates.isNotEmpty(), "附近应有可用劳动者")

        val request = ServiceRequest(
            id = RequestId.random(),
            consumerId = ConsumerId(consumer.id.value),
            type = ServiceType.RIDE_HAILING,
            pickupLocation = sanlitun,
        )
        val matchResult = matchingEngine.match(request, candidates)
        assertNotNull(matchResult, "匹配应成功")
        assertTrue(matchResult!!.distanceMeters < 1000, "距离应在搜索半径内")
        assertEquals(worker.name, matchResult.worker.name)

        // ━━━━━━ 4. 派单 ━━━━━━
        val matchedWorker = matchResult.worker
        val dispatchTask = DispatchTask(
            id = "task-${request.id.value}",
            workerId = matchedWorker.id.value,
            serviceType = ServiceType.RIDE_HAILING,
            pickups = listOf(sanlitun),
            dropoffs = listOf(GeoPoint(39.9150, 116.4040)),  // 故宫
            estimatedDistanceMeters = matchResult.distanceMeters + 3000,
            estimatedDurationMinutes = 20,
        )
        val prefs = WorkerPreferences(
            workerId = matchedWorker.id.value,
            preferredServiceTypes = setOf(ServiceType.RIDE_HAILING),
        )
        assertTrue(
            dispatchService.isAcceptableForWorker(dispatchTask, prefs),
            "任务应符合劳动者偏好",
        )
        val route = dispatchService.optimizeRoute(
            workerLocation = matchedWorker.currentLocation,
            task = dispatchTask,
        )
        assertNotNull(route, "应生成路线建议")

        // ━━━━━━ 5. 分账 ━━━━━━
        val transaction = Transaction(
            id = TransactionId.random(),
            consumerId = consumer.id.value,
            workerId = matchedWorker.id.value,
            amount = BigDecimal("35.50"),
            serviceType = "RIDE_HAILING",
        )
        val charged = paymentService.charge(transaction)
        val settlement = paymentService.settle(charged, SettlementRule.DEFAULT)

        // 反榨取底线：劳动者所得 ≥ 70%
        val workerShare = settlement.workerPayout.divide(settlement.totalAmount, 4, java.math.RoundingMode.HALF_UP)
        assertTrue(workerShare >= BigDecimal("0.70"), "劳动者所得比例不得低于 70%")
        assertEquals(0, BigDecimal("35.50").compareTo(settlement.totalAmount))

        // ━━━━━━ 6. 双向评价 ━━━━━━
        // 消费者 → 劳动者
        val consumerToWorker = Rating(
            id = RatingId.random(),
            transactionId = transaction.id.value,
            raterId = consumer.id.value,
            rateeId = matchedWorker.id.value,
            direction = RatingDirection.CONSUMER_TO_WORKER,
            score = 5,
            tags = setOf(RatingTag.PUNCTUAL, RatingTag.SAFE_DRIVING, RatingTag.POLITE),
            comment = "老王开车很稳，准时到达！",
        )
        ratingService.submit(consumerToWorker)

        // 劳动者 → 消费者
        val workerToConsumer = Rating(
            id = RatingId.random(),
            transactionId = transaction.id.value,
            raterId = matchedWorker.id.value,
            rateeId = consumer.id.value,
            direction = RatingDirection.WORKER_TO_CONSUMER,
            score = 4,
            tags = setOf(RatingTag.POLITE),
        )
        ratingService.submit(workerToConsumer)

        // 验证评价
        val workerProfile = ratingService.getCreditProfile(matchedWorker.id.value)
        assertTrue(workerProfile.totalRatings >= 1)
        assertTrue(workerProfile.averageScore >= 4.0)

        val txRatings = ratingService.findByTransaction(transaction.id.value)
        assertEquals(2, txRatings.size, "一笔交易应有双向评价")
    }

    @Test
    fun `matching rejects when no workers nearby`() {
        // 偏远地区——无劳动者
        val remoteArea = GeoPoint(40.0000, 117.0000)
        val candidates = workerLocationService.findNearbyWorkers(remoteArea, 1000.0)
        val request = ServiceRequest(
            id = RequestId.random(),
            consumerId = ConsumerId("nobody"),
            type = ServiceType.RIDE_HAILING,
            pickupLocation = remoteArea,
        )
        val result = matchingEngine.match(request, candidates)
        assertTrue(result == null, "无候选劳动者时匹配应返回 null")
    }
}
