package com.commonsengine.rating.api

import com.commonsengine.rating.domain.Rating
import com.commonsengine.rating.domain.RatingDirection
import com.commonsengine.rating.domain.RatingId
import com.commonsengine.rating.domain.RatingTag
import com.commonsengine.rating.service.RatingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 信用评价 REST API
 *
 * 核心原则：双向评价 + 反惩罚设计（评价不挂钩接单资格）+ 数据可携带。
 */
@RestController
@RequestMapping("/api/v1/rating")
open class RatingController(
    private val service: RatingService,
) {

    /** 提交评价 */
    @PostMapping("/submit")
    fun submit(@RequestBody body: SubmitRatingRequest): RatingResponse {
        val rating = Rating(
            id = RatingId.random(),
            transactionId = body.transactionId,
            raterId = body.raterId,
            rateeId = body.rateeId,
            direction = RatingDirection.valueOf(body.direction),
            score = body.score,
            tags = body.tags.mapNotNull { runCatching { RatingTag.valueOf(it) }.getOrNull() }.toSet(),
            comment = body.comment,
        )
        val saved = service.submit(rating)
        return saved.toResponse()
    }

    /** 查询某人收到的评价 */
    @GetMapping("/received/{memberId}")
    fun findReceived(@PathVariable memberId: String): List<RatingResponse> =
        service.findReceived(memberId).map { it.toResponse() }

    /** 查询某人发出的评价 */
    @GetMapping("/given/{memberId}")
    fun findGiven(@PathVariable memberId: String): List<RatingResponse> =
        service.findGiven(memberId).map { it.toResponse() }

    /** 查询某笔交易的评价 */
    @GetMapping("/transaction/{transactionId}")
    fun findByTransaction(@PathVariable transactionId: String): List<RatingResponse> =
        service.findByTransaction(transactionId).map { it.toResponse() }

    /** 聚合信用画像 */
    @GetMapping("/profile/{memberId}")
    fun getCreditProfile(@PathVariable memberId: String): CreditProfileResponse {
        val profile = service.getCreditProfile(memberId)
        return CreditProfileResponse(
            memberId = profile.memberId,
            averageScore = profile.averageScore,
            totalRatings = profile.totalRatings,
            tagFrequency = profile.tagFrequency.mapKeys { it.key.name },
            recentScores = profile.recentScores,
        )
    }

    /** 导出信用记录（数据归个人——劳动者可带走） */
    @GetMapping("/export/{memberId}", produces = ["text/plain"])
    fun exportProfile(@PathVariable memberId: String): String =
        service.exportProfile(memberId)
}

// ── DTO ──────────────────────────────────────────────────────────

data class SubmitRatingRequest(
    val transactionId: String,
    val raterId: String,
    val rateeId: String,
    val direction: String,  // WORKER_TO_CONSUMER or CONSUMER_TO_WORKER
    val score: Int,
    val tags: List<String> = emptyList(),
    val comment: String? = null,
)

data class RatingResponse(
    val id: String,
    val transactionId: String,
    val raterId: String,
    val rateeId: String,
    val direction: String,
    val score: Int,
    val tags: List<String>,
    val comment: String?,
    val createdAt: String,
)

data class CreditProfileResponse(
    val memberId: String,
    val averageScore: Double,
    val totalRatings: Int,
    val tagFrequency: Map<String, Int>,
    val recentScores: List<Int>,
)

private fun Rating.toResponse() = RatingResponse(
    id = id.value,
    transactionId = transactionId,
    raterId = raterId,
    rateeId = rateeId,
    direction = direction.name,
    score = score,
    tags = tags.map { it.name },
    comment = comment,
    createdAt = createdAt.toString(),
)
