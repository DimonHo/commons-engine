package com.commonsengine.platform.health

import com.commonsengine.dispatch.service.DispatchService
import com.commonsengine.identity.service.MembershipService
import com.commonsengine.matching.engine.MatchingEngine
import com.commonsengine.payment.ledger.LedgerBook
import com.commonsengine.payment.service.PaymentService
import com.commonsengine.rating.service.RatingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 平台总览——聚合所有模块的健康状态
 */
@RestController
@RequestMapping("/api/v1/platform")
open class PlatformHealthController(
    private val matchingEngine: MatchingEngine,
    private val membershipService: MembershipService,
    private val paymentService: PaymentService,
    private val ratingService: RatingService,
    private val dispatchService: DispatchService,
    private val ledgerBook: LedgerBook,
) {

    @GetMapping("/health")
    open fun health(): Map<String, Any> {
        val memberStats = membershipService.roleStatistics()

        return mapOf(
            "status" to "UP",
            "version" to "0.1.0-SNAPSHOT",
            "modules" to mapOf(
                "matching" to mapOf(
                    "status" to "UP",
                    "currentStrategy" to matchingEngine.currentStrategy(),
                    "availableStrategies" to matchingEngine.availableStrategies(),
                ),
                "identity" to mapOf(
                    "status" to "UP",
                    "totalMembers" to membershipService.findAll().size,
                    "roleBreakdown" to memberStats,
                ),
                "payment" to mapOf(
                    "status" to "UP",
                    "ledgerEvents" to ledgerBook.size(),
                ),
                "rating" to mapOf(
                    "status" to "UP",
                ),
                "dispatch" to mapOf(
                    "status" to "UP",
                ),
            ),
        )
    }
}
