package com.commonsengine.matching.api;

import com.commonsengine.matching.engine.MatchingEngine;
import com.commonsengine.matching.infrastructure.persistence.WorkerLocationEntity;
import com.commonsengine.matching.service.WorkerLocationService;
import com.commonsengine.platform.domain.Model.MatchResult;
import com.commonsengine.platform.domain.Model.ServiceRequest;
import com.commonsengine.platform.domain.Model.Worker;
import com.commonsengine.platform.domain.Model.WorkerId;
import com.commonsengine.platform.domain.Model.ConsumerId;
import com.commonsengine.platform.domain.Model.RequestId;
import com.commonsengine.platform.domain.ServiceType;
import com.commonsengine.platform.geo.GeoPoint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 匹配 REST API（#62 #63）
 *
 * 端点：
 * - GET    /api/v1/matching/strategies           列出可用策略
 * - GET    /api/v1/matching/strategy              查询当前策略
 * - POST   /api/v1/matching/strategy              切换策略
 * - POST   /api/v1/matching/match                 手动匹配
 * - POST   /api/v1/matching/auto-match            自动匹配（基于附近劳动者）
 * - POST   /api/v1/matching/locations/{workerId}  更新劳动者位置
 * - GET    /api/v1/matching/nearby                查询附近劳动者
 */
@RestController
@RequestMapping("/api/v1/matching")
public class MatchingController {

    private final MatchingEngine engine;
    private final WorkerLocationService locationService;

    public MatchingController(MatchingEngine engine, WorkerLocationService locationService) {
        this.engine = engine;
        this.locationService = locationService;
    }

    @GetMapping("/strategies")
    public List<String> strategies() {
        return List.copyOf(engine.availableStrategies());
    }

    @GetMapping("/strategy")
    public StrategyRequest currentStrategy() {
        return new StrategyRequest(engine.currentStrategy());
    }

    @PostMapping("/strategy")
    public StrategyRequest switchStrategy(@RequestBody StrategyRequest request) {
        engine.useStrategy(request.name());
        return new StrategyRequest(engine.currentStrategy());
    }

    /**
     * 手动匹配——请求中直接提供候选劳动者列表。
     */
    @PostMapping("/match")
    public ResponseEntity<MatchResponse> match(@RequestBody MatchRequest request) {
        ServiceRequest serviceRequest = buildServiceRequest(request);
        List<Worker> candidates = buildWorkers(request.candidates());

        MatchResult result = engine.match(serviceRequest, candidates);
        if (result == null) {
            return ResponseEntity.ok(MatchResponse.noMatch(engine.currentStrategy()));
        }
        return ResponseEntity.ok(MatchResponse.from(result));
    }

    /**
     * 自动匹配——基于请求位置查询附近劳动者后匹配。
     */
    @PostMapping("/auto-match")
    public ResponseEntity<MatchResponse> autoMatch(@RequestBody AutoMatchRequest request) {
        List<WorkerLocationEntity> nearby = locationService.findNearbyWorkers(
                request.pickupLat(), request.pickupLng(), request.radiusMeters()
        );
        if (nearby.isEmpty()) {
            return ResponseEntity.ok(MatchResponse.noMatch(engine.currentStrategy()));
        }

        ServiceRequest serviceRequest = buildServiceRequestFromAuto(request);
        List<Worker> workers = new ArrayList<>();
        for (WorkerLocationEntity e : nearby) {
            workers.add(buildWorker(e.getWorkerId(), e.getLat(), e.getLng(), e.getActiveOrderCount()));
        }

        MatchResult result = engine.match(serviceRequest, workers);
        if (result == null) {
            return ResponseEntity.ok(MatchResponse.noMatch(engine.currentStrategy()));
        }
        return ResponseEntity.ok(MatchResponse.from(result));
    }

    @PostMapping("/locations/{workerId}")
    public LocationUpdate updateLocation(@PathVariable String workerId,
                                          @RequestBody LocationUpdate update) {
        locationService.updateLocation(workerId, update.lat(), update.lng(),
                update.activeOrderCount());
        return update;
    }

    @GetMapping("/nearby")
    public List<CandidateDto> nearby(@RequestParam double lat,
                                      @RequestParam double lng,
                                      @RequestParam(defaultValue = "5000") double radiusMeters) {
        return locationService.findNearbyWorkers(lat, lng, radiusMeters).stream()
                .map(e -> new CandidateDto(e.getWorkerId(), e.getLat(), e.getLng(),
                        e.getActiveOrderCount()))
                .toList();
    }

    // ── 构造 Java 领域对象 ────────────────────────────────
    // platform-core 域模型为 Java record（JDK 21），ID 为 record 包装类型。

    private ServiceRequest buildServiceRequest(MatchRequest req) {
        return new ServiceRequest(
                RequestId.random(),
                new ConsumerId(req.consumerId()),
                req.serviceType(),
                new GeoPoint(req.pickupLat(), req.pickupLng()),
                new GeoPoint(req.pickupLat(), req.pickupLng())
        );
    }

    private ServiceRequest buildServiceRequestFromAuto(AutoMatchRequest req) {
        return new ServiceRequest(
                RequestId.random(),
                new ConsumerId(req.consumerId()),
                req.serviceType(),
                new GeoPoint(req.pickupLat(), req.pickupLng()),
                new GeoPoint(req.pickupLat(), req.pickupLng())
        );
    }

    private List<Worker> buildWorkers(List<CandidateDto> candidates) {
        List<Worker> workers = new ArrayList<>();
        for (CandidateDto c : candidates) {
            workers.add(buildWorker(c.workerId(), c.lat(), c.lng(), c.activeOrderCount()));
        }
        return workers;
    }

    private Worker buildWorker(String workerId, double lat, double lng, int activeOrderCount) {
        return new Worker(
                new WorkerId(workerId),
                workerId,
                new GeoPoint(lat, lng),
                5.0,
                activeOrderCount,
                Set.of()
        );
    }

    // ── DTO records ────────────────────────────────────────

    public record StrategyRequest(@NotBlank String name) {
    }

    public record MatchRequest(
            @NotBlank String consumerId,
            @NotNull ServiceType serviceType,
            double pickupLat,
            double pickupLng,
            List<CandidateDto> candidates
    ) {
    }

    public record AutoMatchRequest(
            @NotBlank String consumerId,
            @NotNull ServiceType serviceType,
            double pickupLat,
            double pickupLng,
            double radiusMeters
    ) {
    }

    public record CandidateDto(
            @NotBlank String workerId,
            double lat,
            double lng,
            int activeOrderCount
    ) {
    }

    public record LocationUpdate(double lat, double lng, int activeOrderCount) {
    }

    public record MatchResponse(
            boolean matched,
            String workerId,
            Double score,
            String strategy,
            String reason
    ) {
        static MatchResponse from(MatchResult r) {
            // MatchResult 是 record(workerId, requestId, score)
            return new MatchResponse(
                    true,
                    r.workerId().toString(),
                    r.score(),
                    null,
                    "匹配成功"
            );
        }

        static MatchResponse noMatch(String strategy) {
            return new MatchResponse(false, null, null, strategy, "无匹配候选");
        }
    }
}
