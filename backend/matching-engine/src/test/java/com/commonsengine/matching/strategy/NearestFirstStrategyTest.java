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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最近优先策略测试——从 Kotlin NearestFirstStrategyTest 转换。
 *
 * <p>适配 Java API：
 * <ul>
 *   <li>{@code NearestFirstStrategy()} / {@code NearestFirstStrategy(AntiExploitationConfig)}</li>
 *   <li>{@code match(request, candidates)} → MatchResult(workerId, requestId, score)</li>
 *   <li>AntiExploitationConfig 字段：{@code maxActiveOrdersPerWorker}（默认 3）</li>
 * </ul>
 */
class NearestFirstStrategyTest {

    private final GeoPoint pickup = new GeoPoint(39.9042, 116.4074); // 北京天安门

    private ServiceRequest request() {
        return new ServiceRequest(
                RequestId.random(),
                new ConsumerId("c1"),
                ServiceType.RIDE_HAILING,
                pickup,
                new GeoPoint(39.9300, 116.4300)
        );
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

    private Worker worker(String id, double lat, double lng, int orders, double rating) {
        return new Worker(
                new WorkerId(id),
                "worker-" + id,
                new GeoPoint(lat, lng),
                rating,
                orders,
                Set.of()
        );
    }

    @Test
    void selectsNearestWorker() {
        NearestFirstStrategy strategy = new NearestFirstStrategy();
        List<Worker> candidates = List.of(
                worker("far", 39.9200, 116.4200),   // ~2km
                worker("near", 39.9050, 116.4080),  // ~100m
                worker("mid", 39.9100, 116.4100)    // ~700m
        );

        MatchResult result = strategy.match(request(), candidates);

        assertNotNull(result);
        assertEquals("near", result.workerId().value());
        // score = 1/(1+distance)，最近者 score 最高
        assertTrue(result.score() > 0);
        assertEquals("nearest-first", strategy.name());
    }

    @Test
    void filtersOutOverloadedWorkers() {
        // maxActiveOrdersPerWorker = 2 → 活跃订单数 ≥ 2 的劳动者被排除
        NearestFirstStrategy strategy = new NearestFirstStrategy(
                new AntiExploitationConfig(2, 0, 0)
        );
        List<Worker> candidates = List.of(
                worker("overloaded", 39.9050, 116.4080, 3, 5.0),  // 很近但超载
                worker("available", 39.9100, 116.4100, 0, 5.0)    // 稍远但可用
        );

        MatchResult result = strategy.match(request(), candidates);

        assertNotNull(result);
        assertEquals("available", result.workerId().value());
    }

    @Test
    void returnsNullWhenAllCandidatesOverloaded() {
        NearestFirstStrategy strategy = new NearestFirstStrategy(
                new AntiExploitationConfig(0, 0, 0)
        );
        List<Worker> candidates = List.of(
                worker("a", 39.9050, 116.4080, 1, 5.0)  // activeOrderCount=1 > 0
        );

        MatchResult result = strategy.match(request(), candidates);
        assertNull(result);
    }

    @Test
    void emptyCandidateListReturnsNull() {
        NearestFirstStrategy strategy = new NearestFirstStrategy();
        MatchResult result = strategy.match(request(), List.of());
        assertNull(result);
    }
}
