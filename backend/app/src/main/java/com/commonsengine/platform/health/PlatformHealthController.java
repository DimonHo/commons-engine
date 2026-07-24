package com.commonsengine.platform.health;

import com.commonsengine.dispatch.service.DispatchService;
import com.commonsengine.identity.service.MembershipService;
import com.commonsengine.matching.engine.MatchingEngine;
import com.commonsengine.payment.ledger.LedgerBook;
import com.commonsengine.rating.service.RatingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 平台总览——聚合所有模块的健康状态
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformHealthController {

    private final MatchingEngine matchingEngine;
    private final MembershipService membershipService;
    private final LedgerBook ledgerBook;

    public PlatformHealthController(
            MatchingEngine matchingEngine,
            MembershipService membershipService,
            RatingService ratingService,
            DispatchService dispatchService,
            LedgerBook ledgerBook) {
        this.matchingEngine = matchingEngine;
        this.membershipService = membershipService;
        this.ledgerBook = ledgerBook;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        var memberStatsRaw = membershipService.roleStatistics();
        var memberStats = new java.util.LinkedHashMap<String, Integer>();
        memberStatsRaw.forEach((k, v) -> memberStats.put(k.name(), v));

        return Map.of(
                "status", "UP",
                "version", "0.1.0-SNAPSHOT",
                "modules", Map.of(
                        "matching", Map.of(
                                "status", "UP",
                                "currentStrategy", matchingEngine.currentStrategy(),
                                "availableStrategies", matchingEngine.availableStrategies()
                        ),
                        "identity", Map.of(
                                "status", "UP",
                                "totalMembers", membershipService.findAll().size(),
                                "roleBreakdown", memberStats
                        ),
                        "payment", Map.of(
                                "status", "UP",
                                "ledgerEvents", ledgerBook.size()
                        ),
                        "rating", Map.of(
                                "status", "UP"
                        ),
                        "dispatch", Map.of(
                                "status", "UP"
                        )
                )
        );
    }
}
