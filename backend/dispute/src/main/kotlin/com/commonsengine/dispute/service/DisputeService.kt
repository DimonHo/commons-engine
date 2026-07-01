package com.commonsengine.dispute.service

import com.commonsengine.dispute.domain.AiScreeningResult
import com.commonsengine.dispute.domain.ArbitrationVerdict
import com.commonsengine.dispute.domain.Dispute
import com.commonsengine.dispute.domain.DisputeId
import com.commonsengine.dispute.domain.DisputePriority
import com.commonsengine.dispute.domain.DisputeStatus
import com.commonsengine.dispute.domain.DisputeType
import com.commonsengine.dispute.domain.VerdictType
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 纠纷仲裁服务
 *
 * 流程：提交 → AI 初筛 → (自动解决 | 调查 → 仲裁) → 解决
 *
 * 设计原则（架构文档 3.4 条）：
 * 1. AI 初筛 + 人工仲裁：AI 做初步分类和证据整理，复杂纠纷由仲裁委员会处理
 * 2. 透明流程：仲裁规则公开，劳动者有权申诉、有权知道仲裁结果的理由
 * 3. 仲裁委员会由劳动者代表、用户代表和社区代表组成
 */
@Service
open class DisputeService {

    private val disputes = ConcurrentHashMap<String, Dispute>()

    /** 提交纠纷工单 */
    fun file(
        transactionId: String,
        filedBy: String,
        filedAgainst: String,
        type: DisputeType,
        description: String,
        evidenceUrls: List<String> = emptyList(),
    ): Dispute {
        val dispute = Dispute(
            id = DisputeId.random(),
            transactionId = transactionId,
            filedBy = filedBy,
            filedAgainst = filedAgainst,
            type = type,
            description = description,
            evidenceUrls = evidenceUrls,
        )
        disputes[dispute.id.value] = dispute
        return dispute
    }

    /**
     * AI 初筛——对纠纷进行分类、评级和证据摘要
     *
     * MVP 阶段使用规则匹配，后续接入 AI 服务层 NLP 模型。
     */
    fun aiScreening(disputeId: DisputeId): AiScreeningResult {
        val dispute = disputes[disputeId.value]
            ?: throw IllegalArgumentException("纠纷工单不存在: $disputeId")

        updateStatus(disputeId, DisputeStatus.AI_REVIEW)

        // 规则匹配：根据类型和描述关键词判断优先级
        val priority = when {
            dispute.type in listOf(DisputeType.BEHAVIORAL, DisputeType.DAMAGE_CLAIM) ->
                DisputePriority.HIGH
            dispute.type in listOf(DisputeType.FARE_DISPUTE, DisputeType.PAYMENT_ISSUE) ->
                DisputePriority.MEDIUM
            else -> DisputePriority.LOW
        }

        // 简单纠纷可以自动解决（低优先级 + 有明确证据）
        val canAutoResolve = priority == DisputePriority.LOW && dispute.evidenceUrls.isNotEmpty()

        // 证据摘要（MVP：截取描述前 200 字）
        val summary = if (dispute.description.length > 200) {
            dispute.description.take(200) + "..."
        } else {
            dispute.description
        }

        val result = AiScreeningResult(
            disputeId = disputeId,
            canAutoResolve = canAutoResolve,
            suggestedPriority = priority,
            summary = summary,
            category = dispute.type,
            confidence = if (canAutoResolve) 0.85 else 0.60,
            reasoning = buildString {
                append("AI 初筛：类型=${dispute.type}，")
                append("建议优先级=$priority。")
                if (canAutoResolve) {
                    append("低优先级且有证据，建议自动解决。")
                } else {
                    append("需人工调查，转入调查流程。")
                }
            },
        )

        // 根据初筛结果更新状态
        if (canAutoResolve) {
            updateStatus(disputeId, DisputeStatus.AUTO_RESOLVED)
        } else {
            updateStatus(disputeId, DisputeStatus.INVESTIGATION)
        }

        return result
    }

    /**
     * 仲裁——由仲裁委员会做出裁决
     *
     * 仲裁委员会由劳动者代表、用户代表和社区代表组成（比例见治理细则）。
     */
    fun arbitrate(
        disputeId: DisputeId,
        verdict: VerdictType,
        reasoning: String,
        compensationAmount: java.math.BigDecimal? = null,
    ): ArbitrationVerdict {
        val dispute = disputes[disputeId.value]
            ?: throw IllegalArgumentException("纠纷工单不存在: $disputeId")

        require(dispute.status in listOf(DisputeStatus.INVESTIGATION, DisputeStatus.AI_REVIEW)) {
            "工单必须处于调查或初筛阶段才能仲裁，当前: ${dispute.status}"
        }

        updateStatus(disputeId, DisputeStatus.ARBITRATION)

        val arbVerdict = ArbitrationVerdict(
            disputeId = disputeId,
            verdict = verdict,
            reasoning = reasoning,
            compensationAmount = compensationAmount,
        )

        disputes[disputeId.value] = dispute.copy(
            status = DisputeStatus.RESOLVED,
            updatedAt = Instant.now(),
            resolution = "裁决：$verdict。理由：$reasoning",
            resolvedAt = Instant.now(),
        )

        return arbVerdict
    }

    /** 查询工单 */
    fun findById(id: DisputeId): Dispute? = disputes[id.value]

    /** 查询全部工单 */
    fun findAll(): List<Dispute> = disputes.values.toList()

    /** 按状态查询 */
    fun findByStatus(status: DisputeStatus): List<Dispute> =
        disputes.values.filter { it.status == status }

    private fun updateStatus(id: DisputeId, status: DisputeStatus) {
        disputes[id.value]?.let { d ->
            disputes[id.value] = d.copy(status = status, updatedAt = Instant.now())
        }
    }
}
