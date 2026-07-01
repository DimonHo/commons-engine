package com.commonsengine.rating.service

import com.commonsengine.rating.domain.CreditProfile
import com.commonsengine.rating.domain.Rating
import com.commonsengine.rating.domain.RatingDirection
import com.commonsengine.rating.domain.RatingId
import com.commonsengine.rating.domain.RatingTag
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 信用评价服务
 *
 * 核心原则：
 * 1. 双向评价——劳动者也能评价消费者
 * 2. 反惩罚——评价不挂钩接单资格，仅作参考
 * 3. 数据可携带——劳动者可导出自己的信用记录
 */
@Service
open class RatingService {

    private val ratings = ConcurrentHashMap<String, Rating>()

    /** 提交评价 */
    fun submit(rating: Rating): Rating {
        ratings[rating.id.value] = rating
        return rating
    }

    /** 查询某人收到的所有评价 */
    fun findReceived(memberId: String): List<Rating> =
        ratings.values.filter { it.rateeId == memberId }

    /** 查询某人发出的所有评价 */
    fun findGiven(memberId: String): List<Rating> =
        ratings.values.filter { it.raterId == memberId }

    /** 查询某笔交易的评价（双方向） */
    fun findByTransaction(transactionId: String): List<Rating> =
        ratings.values.filter { it.transactionId == transactionId }

    /**
     * 聚合信用画像
     */
    fun getCreditProfile(memberId: String): CreditProfile {
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
    fun exportProfile(memberId: String): String {
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
