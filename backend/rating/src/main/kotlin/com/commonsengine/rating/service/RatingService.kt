package com.commonsengine.rating.service

import com.commonsengine.rating.domain.CreditProfile
import com.commonsengine.rating.domain.Rating
import com.commonsengine.rating.domain.RatingId
import com.commonsengine.rating.infrastructure.persistence.RatingRepository
import com.commonsengine.rating.infrastructure.persistence.toDomain
import com.commonsengine.rating.infrastructure.persistence.toEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 信用评价服务
 *
 * 核心原则：
 * 1. 双向评价——劳动者也能评价消费者
 * 2. 反惩罚——评价不挂钩接单资格，仅作参考
 * 3. 数据可携带——劳动者可导出自己的信用记录
 *
 * 持久化：使用 JPA + PostgreSQL，重启不丢数据。
 */
@Service
open class RatingService(
    private val repository: RatingRepository,
) {

    /** 提交评价 */
    @Transactional
    open fun submit(rating: Rating): Rating {
        repository.save(rating.toEntity())
        return rating
    }

    /** 查询某人收到的所有评价 */
    @Transactional(readOnly = true)
    open fun findReceived(memberId: String): List<Rating> =
        repository.findByRateeId(memberId).map { it.toDomain() }

    /** 查询某人发出的所有评价 */
    @Transactional(readOnly = true)
    open fun findGiven(memberId: String): List<Rating> =
        repository.findByRaterId(memberId).map { it.toDomain() }

    /** 查询某笔交易的评价（双方向） */
    @Transactional(readOnly = true)
    open fun findByTransaction(transactionId: String): List<Rating> =
        repository.findByTransactionId(transactionId).map { it.toDomain() }

    /**
     * 聚合信用画像
     */
    @Transactional(readOnly = true)
    open fun getCreditProfile(memberId: String): CreditProfile {
        val received = findReceived(memberId)
        if (received.isEmpty()) {
            return CreditProfile(
                memberId = memberId,
                averageScore = 5.0,
                totalRatings = 0,
                tagFrequency = emptyMap(),
                recentScores = emptyList(),
            )
        }

        val scores = received.map { it.score }
        val tagFreq = received.flatMap { it.tags }.groupingBy { it }.eachCount()

        return CreditProfile(
            memberId = memberId,
            averageScore = scores.average(),
            totalRatings = received.size,
            tagFrequency = tagFreq,
            recentScores = scores.takeLast(20),  // 最近 20 条
        )
    }

    /**
     * 导出信用记录（数据归个人——劳动者可带走）
     */
    @Transactional(readOnly = true)
    open fun exportProfile(memberId: String): String {
        val profile = getCreditProfile(memberId)
        val received = findReceived(memberId)

        return buildString {
            appendLine("=== 公地引擎 · 信用记录导出 ===")
            appendLine("成员 ID: ${profile.memberId}")
            appendLine("平均评分: ${"%.2f".format(profile.averageScore)}")
            appendLine("评价总数: ${profile.totalRatings}")
            appendLine()
            appendLine("标签统计:")
            profile.tagFrequency.forEach { (tag, count) ->
                appendLine("  ${tag.name}: $count 次")
            }
            appendLine()
            appendLine("评价明细:")
            received.forEach { r ->
                appendLine("  [${r.createdAt}] 分数:${r.score} 标签:${r.tags.joinToString(",") { it.name }} ${r.comment ?: ""}")
            }
            appendLine()
            appendLine("注：此信用记录归您个人所有，可携带至其他合作社。")
        }
    }
}
