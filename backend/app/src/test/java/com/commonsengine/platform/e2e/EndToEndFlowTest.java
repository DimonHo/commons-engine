package com.commonsengine.platform.e2e;

import com.commonsengine.dispatch.domain.Model.DispatchTask;
import com.commonsengine.dispatch.domain.Model.RouteSuggestion;
import com.commonsengine.dispatch.domain.Model.WorkerPreferences;
import com.commonsengine.dispatch.service.DispatchService;
import com.commonsengine.identity.domain.MemberRole;
import com.commonsengine.identity.domain.Model.Member;
import com.commonsengine.identity.service.MembershipService;
import com.commonsengine.matching.engine.MatchingEngine;
import com.commonsengine.matching.service.WorkerLocationService;
import com.commonsengine.payment.domain.Model.SettlementResult;
import com.commonsengine.payment.domain.Model.SettlementRule;
import com.commonsengine.payment.domain.Model.Transaction;
import com.commonsengine.payment.domain.Model.TransactionId;
import com.commonsengine.payment.service.PaymentService;
import com.commonsengine.platform.domain.Model.ConsumerId;
import com.commonsengine.platform.domain.Model.RequestId;
import com.commonsengine.platform.domain.Model.ServiceRequest;
import com.commonsengine.platform.domain.Model.Worker;
import com.commonsengine.platform.domain.Model.WorkerId;
import com.commonsengine.platform.domain.ServiceType;
import com.commonsengine.platform.geo.GeoPoint;
import com.commonsengine.rating.domain.Model.CreditProfile;
import com.commonsengine.rating.domain.Model.Rating;
import com.commonsengine.rating.domain.Model.RatingDirection;
import com.commonsengine.rating.domain.Model.RatingId;
import com.commonsengine.rating.domain.Model.RatingTag;
import com.commonsengine.rating.service.RatingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端全链路集成测试 (#40)
 *
 * 验证公地引擎完整业务流程：
 * 注册 → 位置上报 → 匹配 → 派单 → 分账 → 评价
 *
 * 这条链路是阶段1 的验收标准——证明五大模块能协同工作。
 *
 * <p>适配 Java API：service 方法签名与 Kotlin 原版有差异，但业务链路不变。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EndToEndFlowTest {

    @Autowired
    private MembershipService membershipService;
    @Autowired
    private WorkerLocationService workerLocationService;
    @Autowired
    private MatchingEngine matchingEngine;
    @Autowired
    private DispatchService dispatchService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private RatingService ratingService;

    @Test
    void fullFlowConsumerRequestsRideWorkerMatchedDispatchedPaidRated() {
        // ━━━━━━ 1. 注册：消费者 + 劳动者 ━━━━━━
        Member consumer = membershipService.register(
                "乘客小明",
                "13800001111",
                Set.of(MemberRole.CONSUMER)
        );
        Member worker = membershipService.register(
                "司机老王",
                "13800002222",
                Set.of(MemberRole.WORKER)
        );
        assertNotNull(consumer.id().value());
        assertNotNull(worker.id().value());
        assertEquals(MemberRole.WORKER, worker.roles().iterator().next());

        // ━━━━━━ 2. 劳动者上报位置 ━━━━━━
        // 北京三里屯附近
        GeoPoint sanlitun = new GeoPoint(39.9332, 116.4543);
        // 劳动者在消费者 100 米外
        double workerLat = sanlitun.lat() + 0.001;
        double workerLng = sanlitun.lng() + 0.001;
        workerLocationService.updateLocation(
                worker.id().value(),
                workerLat,
                workerLng,
                0
        );

        // ━━━━━━ 3. 匹配：消费者叫车 ━━━━━━
        // WorkerLocationService.findNearbyWorkers 返回实体；匹配引擎接受领域 Worker 对象。
        var nearby = workerLocationService.findNearbyWorkers(
                sanlitun.lat(),
                sanlitun.lng(),
                1000.0
        );
        assertTrue(!nearby.isEmpty(), "附近应有可用劳动者");

        // 构造领域 Worker 候选（匹配引擎的契约）
        Worker workerCandidate = new Worker(
                new WorkerId(worker.id().value()),
                worker.name(),
                new GeoPoint(workerLat, workerLng),
                4.9,
                0,
                Set.of(ServiceType.RIDE_HAILING)
        );

        GeoPoint dropoff = new GeoPoint(39.9150, 116.4040);  // 故宫
        ServiceRequest request = new ServiceRequest(
                RequestId.random(),
                new ConsumerId(consumer.id().value()),
                ServiceType.RIDE_HAILING,
                sanlitun,
                dropoff
        );
        var matchResult = matchingEngine.match(request, List.of(workerCandidate));
        assertNotNull(matchResult, "匹配应成功");
        assertEquals(worker.id().value(), matchResult.workerId().value());

        // ━━━━━━ 4. 派单 ━━━━━━
        DispatchTask createdTask = DispatchTask.create("RIDE_HAILING", sanlitun.lat(), sanlitun.lng());
        DispatchTask dispatchTask = dispatchService.assignTask(createdTask.id(), worker.id().value());
        assertEquals(worker.id().value(), dispatchTask.assignedWorkerId());

        WorkerPreferences prefs = new WorkerPreferences(
                worker.id().value(),
                Set.of("RIDE_HAILING"),
                null,
                10_000.0
        );
        dispatchService.savePreferences(prefs);

        // 路径优化：劳动者当前位置 → pickup → dropoff
        RouteSuggestion route = dispatchService.optimizeRoute(
                workerLat, workerLng,
                List.of(
                        new double[]{sanlitun.lat(), sanlitun.lng()},
                        new double[]{dropoff.lat(), dropoff.lng()}
                ),
                worker.id().value()
        );
        assertNotNull(route, "应生成路线建议");
        assertEquals(2, route.visits().size());

        // ━━━━━━ 5. 分账 ━━━━━━
        Transaction transaction = new Transaction(
                TransactionId.random(),
                consumer.id().value(),
                worker.id().value(),
                new BigDecimal("35.50"),
                "RIDE_HAILING",
                Instant.now(),
                null
        );
        Transaction charged = paymentService.charge(transaction);
        SettlementResult settlement = paymentService.settle(charged, SettlementRule.DEFAULT);

        // 反榨取底线：劳动者所得 ≥ 70%
        BigDecimal workerShare = settlement.workerPayout().divide(settlement.totalAmount(), 4, RoundingMode.HALF_UP);
        assertTrue(workerShare.compareTo(new BigDecimal("0.70")) >= 0, "劳动者所得比例不得低于 70%");
        assertEquals(0, new BigDecimal("35.50").compareTo(settlement.totalAmount()));

        // ━━━━━━ 6. 双向评价 ━━━━━━
        // 消费者 → 劳动者
        Rating consumerToWorker = new Rating(
                RatingId.random(),
                transaction.id().value(),
                consumer.id().value(),
                worker.id().value(),
                RatingDirection.CONSUMER_TO_WORKER,
                5,
                Set.of(RatingTag.PUNCTUAL, RatingTag.SAFE_DRIVING, RatingTag.POLITE),
                "老王开车很稳，准时到达！",
                null
        );
        ratingService.submit(consumerToWorker);

        // 劳动者 → 消费者
        Rating workerToConsumer = new Rating(
                RatingId.random(),
                transaction.id().value(),
                worker.id().value(),
                consumer.id().value(),
                RatingDirection.WORKER_TO_CONSUMER,
                4,
                Set.of(RatingTag.POLITE),
                null,
                null
        );
        ratingService.submit(workerToConsumer);

        // 验证评价
        CreditProfile workerProfile = ratingService.getCreditProfile(worker.id().value());
        assertTrue(workerProfile.totalRatings() >= 1);
        assertTrue(workerProfile.averageScore() >= 4.0);

        List<Rating> txRatings = ratingService.findByTransaction(transaction.id().value());
        assertEquals(2, txRatings.size(), "一笔交易应有双向评价");
    }

    @Test
    void matchingRejectsWhenNoWorkersNearby() {
        // 偏远地区——无劳动者
        GeoPoint remoteArea = new GeoPoint(40.0000, 117.0000);
        var nearby = workerLocationService.findNearbyWorkers(remoteArea.lat(), remoteArea.lng(), 1000.0);
        assertTrue(nearby.isEmpty(), "偏远地区应无劳动者");

        // 匹配引擎：空候选列表 → 返回 null
        GeoPoint dropoff = new GeoPoint(40.0100, 117.0100);
        ServiceRequest request = new ServiceRequest(
                RequestId.random(),
                new ConsumerId("nobody"),
                ServiceType.RIDE_HAILING,
                remoteArea,
                dropoff
        );
        var result = matchingEngine.match(request, List.of());
        assertTrue(result == null, "无候选劳动者时匹配应返回 null");
    }
}
