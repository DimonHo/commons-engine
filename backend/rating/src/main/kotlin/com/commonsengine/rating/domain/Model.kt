package com.commonsengine.rating.domain

import java.time.Instant
import java.util.UUID

/**
 * 评价方向——双向评价，打破单向权力
 */
enum class RatingDirection {
    WORKER_TO_CONSUMER,    // 劳动者评价消费者
    CONSUMER_TO_WORKER,    // 消费者评价劳动者
}

/**
 * 评价标签——标准化评价维度，避免恶意低分
 */
enum class RatingTag {
    POLITE,           // 礼貌
    PUNCTUAL,         // 守时
    PROFESSIONAL,     // 专业
    SAFE_DRIVING,     // 安全驾驶
    GOOD_COMMUNICATION, // 沟通顺畅
    CLEAN,            // 整洁
    PATIENT,          // 耐心
    FAIR,             // 公平
}

/**
 * 单条评价
 *
 * 反惩罚性设计（章程第 3.3 条）：
 * - 评价不直接挂钩接单资格（不自动降权或停派）
 * - 仅作为参考信息
 */
data class Rating(
    val id: RatingId,
    val transactionId: String,
    val raterId: String,        // 评价者 ID
    val rateeId: String,        // 被评价者 ID
    val direction: RatingDirection,
    val score: Int,             // 1-5 分
    val tags: Set<RatingTag> = emptySet(),
    val comment: String? = null,
    val createdAt: Instant = Instant.now(),
) {
    init {
        require(score in 1..5) { "评分必须在 1-5 范围内，实际: $score" }
    }
}

@JvmInline
value class RatingId(val value: String) {
    companion object { fun random() = RatingId(UUID.randomUUID().toString()) }
}

/**
 * 信用画像——聚合某人的所有评价
 *
 * 劳动者离开合作社时可以带走自己的信用记录（数据归个人）。
 */
data class CreditProfile(
    val memberId: String,
    val averageScore: Double,
    val totalRatings: Int,
    val tagFrequency: Map<RatingTag, Int>,  // 各标签出现次数
    val recentScores: List<Int>,             // 最近 N 条评分
)
