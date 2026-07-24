package com.commonsengine.matching.strategy;

import com.commonsengine.platform.domain.Model.ConsumerId;
import com.commonsengine.platform.domain.Model.MatchResult;
import com.commonsengine.platform.domain.Model.RequestId;
import com.commonsengine.platform.domain.Model.ServiceRequest;
import com.commonsengine.platform.domain.Model.Worker;
import com.commonsengine.platform.domain.Model.WorkerId;
import com.commonsengine.platform.domain.ServiceType;
import com.commonsengine.platform.geo.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公平轮转策略测试——从 Kotlin FairRoundRobinStrategyTest 转换。
 *
 * <p>适配 Java API：
 * <ul>
 *   <li>{@code FairRoundRobinStrategy()} / {@code FairRoundRobinStrategy(AntiExploitationConfig)}</li>
 *   <li>{@code match(request, candidates)} → MatchResult(workerId, requestId, score)，无合适候选返回 null</li>
 *   <li>AntiExploitationConfig 字段：{@code maxActiveOrdersPerWorker}</li>
 * </ul>
 */
class FairRoundRobinStrategyTest {

    private final GeoPoint pickup = new GeoPoint(39.9042, 116.4074);

    private ServiceRequest request() {
        return new ServiceRequest(
                RequestId.random(),
                new ConsumerId("c1"),
                ServiceType.RIDE_HAILING,
                pickup,
                new GeoPoint(39.9300, 116.4300)
        );
    }

    private Worker worker(String id) {
        return worker(id, 39.9050, 116.4080);
    }

    private Worker worker(String id, double lat, double lng) {
        return new Worker(
                new WorkerId(id),
                "worker-" + id,
                new GeoPoint(lat, lng),
                5.0,
                0,
                Set.of()
        );
    }

    @Test
    void rotatesBetweenEquallyDistantWorkers() {
        FairRoundRobinStrategy strategy = new FairRoundRobinStrategy();

        // 三个等距劳动者
        List<Worker> candidates = List.of(
                worker("A", 39.9050, 116.4080),
                worker("B", 39.9050, 116.4080),
                worker("C", 39.9050, 116.4080)
        );

        // 第一次匹配
        MatchResult r1 = strategy.match(request(), candidates);
        assertNotNull(r1);

        // 第二次 → 应轮转到不同劳动者（rotationCounter 递增）
        MatchResult r2 = strategy.match(request(), candidates);
        assertNotNull(r2);
        assertTrue(!r2.workerId().equals(r1.workerId()),
                "第二次匹配应轮转到不同劳动者");

        // 第三次 → 第三个劳动者
        MatchResult r3 = strategy.match(request(), candidates);
        assertNotNull(r3);
        Set<String> seen = new HashSet<>();
        seen.add(r1.workerId().value());
        seen.add(r2.workerId().value());
        assertTrue(!seen.contains(r3.workerId().value()),
                "第三次匹配应轮转到第三个劳动者");

        // 第四次 → 回到第一个（轮回完成）
        MatchResult r4 = strategy.match(request(), candidates);
        assertNotNull(r4);
        assertEquals(r1.workerId(), r4.workerId(), "第四次应轮回回第一个");
    }

    @Test
    void matchReturnsResultWithinDistanceThreshold() {
        FairRoundRobinStrategy strategy = new FairRoundRobinStrategy();
        List<Worker> candidates = List.of(worker("A"), worker("B"));

        MatchResult result = strategy.match(request(), candidates);
        assertNotNull(result);
        assertEquals("fair-round-robin", strategy.name());
    }

    @Test
    void returnsNullWhenNoEligibleCandidates() {
        // maxActiveOrdersPerWorker = 0 → 所有超载劳动者都被排除
        FairRoundRobinStrategy strategy = new FairRoundRobinStrategy(
                new AntiExploitationConfig(0, 0, 0)
        );
        List<Worker> candidates = List.of(
                new Worker(
                        new WorkerId("overloaded"),
                        "busy",
                        new GeoPoint(39.9050, 116.4080),
                        5.0,
                        1,
                        Set.of()
                )
        );

        MatchResult result = strategy.match(request(), candidates);
        assertNull(result);
    }

    @Test
    void returnsNullForEmptyCandidates() {
        FairRoundRobinStrategy strategy = new FairRoundRobinStrategy();
        MatchResult result = strategy.match(request(), List.of());
        assertNull(result);
    }
}
