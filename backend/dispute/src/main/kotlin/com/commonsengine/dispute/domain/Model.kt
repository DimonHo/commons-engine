package com.commonsengine.dispute.domain

import java.time.Instant
import java.util.UUID

/**
 * 纠纷类型
 */
enum class DisputeType {
    FARE_DISPUTE,           // 费用争议
    SERVICE_QUALITY,        // 服务质量
    CANCELLATION,           // 取消订单
    DAMAGE_CLAIM,           // 损坏赔偿
    BEHAVIORAL,             // 行为违规（辱骂/骚扰）
    PAYMENT_ISSUE,          // 支付问题
    OTHER,
}

/**
 * 纠纷优先级
 */
enum class DisputePriority {
    LOW,      // P3 常规投诉
    MEDIUM,   // P2 需要调查
    HIGH,     // P1 涉及资金/安全
    URGENT,   // P0 紧急（安全/法律风险）
}

/**
 * 纠纷状态——状态机
 *
 * 流程：FILE → AI_REVIEW → INVESTIGATION → ARBITRATION → RESOLVED
 *                            ↓
 *                       AUTO_RESOLVED（AI 判定简单纠纷直接解决）
 *                                                  ↓
 *                                              ESCALATED（升级到人工仲裁委员会）
 */
enum class DisputeStatus {
    FILED,            // 已提交
    AI_REVIEW,        // AI 初筛中
    INVESTIGATION,    // 调查中
    AUTO_RESOLVED,    // AI 自动解决（简单纠纷）
    ARBITRATION,      // 仲裁中（复杂纠纷，由仲裁委员会处理）
    RESOLVED,         // 已解决
    ESCALATED,        // 已升级（需人工介入）
    WITHDRAWN,        // 已撤回
}

/**
 * 纠纷工单
 */
data class Dispute(
    val id: DisputeId,
    val transactionId: String,
    val filedBy: String,           // 提交者 ID
    val filedAgainst: String,      // 被投诉者 ID
    val type: DisputeType,
    val priority: DisputePriority = DisputePriority.LOW,
    val description: String,
    val evidenceUrls: List<String> = emptyList(),  // 证据（截图/录音 URL）
    val status: DisputeStatus = DisputeStatus.FILED,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val resolution: String? = null,
    val resolvedAt: Instant? = null,
)

@JvmInline
value class DisputeId(val value: String) {
    companion object { fun random() = DisputeId(UUID.randomUUID().toString()) }
}

/**
 * AI 初筛结果
 */
data class AiScreeningResult(
    val disputeId: DisputeId,
    val canAutoResolve: Boolean,       // 是否可自动解决
    val suggestedPriority: DisputePriority,
    val summary: String,                // 证据摘要
    val category: DisputeType,
    val confidence: Double,             // 置信度 0-1
    val reasoning: String,              // AI 判断理由（透明化）
)

/**
 * 仲裁裁决
 */
data class ArbitrationVerdict(
    val disputeId: DisputeId,
    val verdict: VerdictType,
    val reasoning: String,              // 裁决理由（必须公开）
    val compensationAmount: java.math.BigDecimal? = null,
    val decidedAt: Instant = Instant.now(),
)

enum class VerdictType {
    FAVOR_FILER,        // 支持投诉方
    FAVOR_RESPONDENT,   // 支持被投诉方
    COMPROMISE,         // 各退一步
    INSUFFICIENT_EVIDENCE,  // 证据不足
}
