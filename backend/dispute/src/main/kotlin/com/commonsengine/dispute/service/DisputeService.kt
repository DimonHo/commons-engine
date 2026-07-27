package com.commonsengine.dispute.service

import com.commonsengine.dispute.domain.AiScreeningResult
import com.commonsengine.dispute.domain.ArbitrationVerdict
import com.commonsengine.dispute.domain.Dispute
import com.commonsengine.dispute.domain.DisputeId
import com.commonsengine.dispute.domain.DisputePriority
import com.commonsengine.dispute.domain.DisputeStatus
import com.commonsengine.dispute.domain.DisputeType
import com.commonsengine.dispute.domain.VerdictType
import com.commonsengine.dispute.infrastructure.persistence.DisputeEntity
import com.commonsengine.dispute.infrastructure.persistence.DisputeRepository
import com.commonsengine.dispute.infrastructure.persistence.toDomain
import com.commonsengine.dispute.infrastructure.persistence.toEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 纠纷仲裁服务
 *
 * 流程：提交 → AI 初筛 → (自动解决 | 调查 → 仲裁) → 解决
 *
 * 设计原则（架构文档 3.4 条）：
 * 1. AI 初筛 + 人工仲裁：AI 做初步分类和证据整理，复杂纠纷由仲裁委员会处理
 * 2. 透明流程：仲裁规则公开，劳动者有权申诉、有权知道仲裁结果的理由
 * 3. 仲裁委员会由劳动者代表、用户代表和社区代表组成
 *
 * 持久化：使用 JPA + PostgreSQL，重启不丢数据。
 */
@Service
open class DisputeService(
    private val repository: DisputeRepository,
) {

    /** 提交纠纷工单 */
    @Transactional
    open fun file(
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
        repository.save(dispute.toEntity())
        return dispute
    }

    /**
     * AI 初筛——对纠纷进行分类、评级和证据摘要
     *
     * MVP 阶段使用规则匹配，后续接入 AI 服务层 NLP 模型。
     */
    @Transactional
    open fun aiScreening(disputeId: DisputeId): AiScreeningResult {
        val entity = findByDisputeIdOrThrow(disputeId)
        val dispute = entity.toDomain()

        updateEntityStatus(entity, DisputeStatus.AI_REVIEW)

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
            updateEntityStatus(entity, DisputeStatus.AUTO_RESOLVED)
        } else {
            updateEntityStatus(entity, DisputeStatus.INVESTIGATION)
        }

        return result
    }

    /**
     * 仲裁——由仲裁委员会做出裁决
     *
     * 仲裁委员会由劳动者代表、用户代表和社区代表组成（比例见治理细则）。
     */
    @Transactional
    open fun arbitrate(
        disputeId: DisputeId,
        verdict: VerdictType,
        reasoning: String,
        compensationAmount: java.math.BigDecimal? = null,
    ): ArbitrationVerdict {
        val entity = findByDisputeIdOrThrow(disputeId)
        val dispute = entity.toDomain()

        require(dispute.status in listOf(DisputeStatus.INVESTIGATION, DisputeStatus.AI_REVIEW)) {
            "工单必须处于调查或初筛阶段才能仲裁，当前: ${dispute.status}"
        }

        updateEntityStatus(entity, DisputeStatus.ARBITRATION)

        val arbVerdict = ArbitrationVerdict(
            disputeId = disputeId,
            verdict = verdict,
            reasoning = reasoning,
            compensationAmount = compensationAmount,
        )

        // 直接修改已有 entity 并保存——保留数据库主键
        entity.status = DisputeStatus.RESOLVED
        entity.updatedAt = Instant.now()
        entity.resolution = "裁决：$verdict。理由：$reasoning"
        entity.resolvedAt = Instant.now()
        repository.save(entity)

        return arbVerdict
    }

    /** 查询工单 */
    @Transactional(readOnly = true)
    open fun findById(id: DisputeId): Dispute? =
        repository.findByDisputeId(id.value)?.toDomain()

    /** 查询全部工单 */
    @Transactional(readOnly = true)
    open fun findAll(): List<Dispute> =
        repository.findAll().map { it.toDomain() }

    /** 按状态查询 */
    @Transactional(readOnly = true)
    open fun findByStatus(status: DisputeStatus): List<Dispute> =
        repository.findByStatus(status).map { it.toDomain() }

    private fun findByDisputeIdOrThrow(disputeId: DisputeId): DisputeEntity =
        repository.findByDisputeId(disputeId.value)
            ?: throw IllegalArgumentException("纠纷工单不存在: $disputeId")

    private fun updateEntityStatus(entity: DisputeEntity, status: DisputeStatus) {
        entity.status = status
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }
}
