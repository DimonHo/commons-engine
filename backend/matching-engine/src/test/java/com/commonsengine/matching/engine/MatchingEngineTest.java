package com.commonsengine.matching.engine;

import com.commonsengine.platform.domain.Model.ConsumerId;
import com.commonsengine.platform.domain.Model.MatchResult;
import com.commonsengine.platform.domain.Model.RequestId;
import com.commonsengine.platform.domain.Model.ServiceRequest;
import com.commonsengine.platform.domain.Model.Worker;
import com.commonsengine.platform.domain.Model.WorkerId;
import com.commonsengine.platform.domain.ServiceType;
import com.commonsengine.platform.exception.BusinessRuleException;
import com.commonsengine.platform.geo.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 匹配引擎测试——从 Kotlin MatchingEngineTest 转换。
 *
 * <p>适配 Java MatchingEngine API：
 * <ul>
 *   <li>默认策略为 {@code fair-round-robin}（非 nearest-first）</li>
 *   <li>{@code useStrategy(name)}（非 setStrategy）</li>
 *   <li>{@code match(request, candidates)} → MatchResult(workerId, requestId, score)</li>
 * </ul>
 */
class MatchingEngineTest {

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

    private Worker worker(String id, double lat, double lng) {
        return new Worker(
                new WorkerId(id),
                "w-" + id,
                new GeoPoint(lat, lng),
                5.0,
                0,
                Set.of()
        );
    }

    @Test
    void defaultStrategyIsFairRoundRobin() {
        MatchingEngine engine = new MatchingEngine();
        assertEquals("fair-round-robin", engine.currentStrategy());
    }

    @Test
    void canSwitchStrategyAtRuntime() {
        MatchingEngine engine = new MatchingEngine();
        engine.useStrategy("nearest-first");
        assertEquals("nearest-first", engine.currentStrategy());
    }

    @Test
    void rejectsUnknownStrategy() {
        MatchingEngine engine = new MatchingEngine();
        assertThrows(BusinessRuleException.class,
                () -> engine.useStrategy("greedy-profit-max"));
    }

    @Test
    void matchReturnsResultWithNearestFirst() {
        MatchingEngine engine = new MatchingEngine();
        engine.useStrategy("nearest-first");
        List<Worker> candidates = List.of(
                worker("near", 39.9050, 116.4080),
                worker("far", 39.9200, 116.4200)
        );

        MatchResult result = engine.match(request(), candidates);

        assertNotNull(result);
        assertEquals("near", result.workerId().value());
    }

    @Test
    void matchReturnsNullForEmptyCandidates() {
        MatchingEngine engine = new MatchingEngine();
        MatchResult result = engine.match(request(), List.of());
        assertNull(result);
    }

    @Test
    void availableStrategiesAreListed() {
        MatchingEngine engine = new MatchingEngine();
        Set<String> strategies = engine.availableStrategies();
        assertTrue(strategies.contains("nearest-first"));
        assertTrue(strategies.contains("fair-round-robin"));
    }
}
