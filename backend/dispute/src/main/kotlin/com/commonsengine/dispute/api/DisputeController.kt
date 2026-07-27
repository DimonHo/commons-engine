package com.commonsengine.dispute.api

import com.commonsengine.dispute.domain.DisputeId
import com.commonsengine.dispute.domain.DisputeStatus
import com.commonsengine.dispute.domain.DisputeType
import com.commonsengine.dispute.domain.VerdictType
import com.commonsengine.dispute.service.DisputeService
import com.commonsengine.platform.support.Enums
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/**
 * 纠纷仲裁 REST API
 *
 * 流程：提交纠纷 → AI 初筛 → (自动解决 | 调查 → 仲裁) → 解决
 * 透明流程：仲裁规则公开，劳动者有权申诉（架构文档 3.4 条）。
 */
@RestController
@RequestMapping("/api/v1/dispute")
open class DisputeController(
    private val service: DisputeService,
) {

    /** 提交纠纷工单 */
    @PostMapping("/file")
    fun file(@Valid @RequestBody body: FileDisputeRequest): DisputeResponse {
        val dispute = service.file(
            transactionId = body.transactionId,
            filedBy = body.filedBy,
            filedAgainst = body.filedAgainst,
            type = Enums.parse<DisputeType>(body.type),
            description = body.description,
            evidenceUrls = body.evidenceUrls,
        )
        return dispute.toResponse()
    }

    /** AI 初筛 */
    @PostMapping("/{disputeId}/screening")
    fun aiScreening(@PathVariable disputeId: String): AiScreeningResponse {
        val result = service.aiScreening(DisputeId(disputeId))
        return AiScreeningResponse(
            disputeId = result.disputeId.value,
            canAutoResolve = result.canAutoResolve,
            suggestedPriority = result.suggestedPriority.name,
            summary = result.summary,
            category = result.category.name,
            confidence = result.confidence,
            reasoning = result.reasoning,
        )
    }

    /** 仲裁裁决 */
    @PostMapping("/{disputeId}/arbitrate")
    fun arbitrate(
        @PathVariable disputeId: String,
        @Valid @RequestBody body: ArbitrateRequest,
    ): ArbitrationResponse {
        val verdict = service.arbitrate(
            disputeId = DisputeId(disputeId),
            verdict = Enums.parse<VerdictType>(body.verdict),
            reasoning = body.reasoning,
            compensationAmount = body.compensationAmount?.let { BigDecimal(it) },
        )
        return ArbitrationResponse(
            disputeId = verdict.disputeId.value,
            verdict = verdict.verdict.name,
            reasoning = verdict.reasoning,
            compensationAmount = verdict.compensationAmount?.toString(),
            decidedAt = verdict.decidedAt.toString(),
        )
    }

    /** 查询工单详情 */
    @GetMapping("/{disputeId}")
    fun findById(@PathVariable disputeId: String): DisputeResponse? =
        service.findById(DisputeId(disputeId))?.toResponse()

    /** 查询全部工单（可按状态过滤） */
    @GetMapping
    fun findAll(@RequestParam(required = false) status: String?): List<DisputeResponse> {
        val disputes = if (status != null) {
            service.findByStatus(Enums.parse<DisputeStatus>(status))
        } else {
            service.findAll()
        }
        return disputes.map { it.toResponse() }
    }
}

// ── DTO ──────────────────────────────────────────────────────────

data class FileDisputeRequest(
    @field:NotBlank val transactionId: String,
    @field:NotBlank val filedBy: String,
    @field:NotBlank val filedAgainst: String,
    @field:NotBlank val type: String,
    @field:NotBlank val description: String,
    val evidenceUrls: List<String> = emptyList(),
)

data class DisputeResponse(
    val id: String,
    val transactionId: String,
    val filedBy: String,
    val filedAgainst: String,
    val type: String,
    val priority: String,
    val description: String,
    val status: String,
    val createdAt: String,
    val resolution: String?,
)

data class AiScreeningResponse(
    val disputeId: String,
    val canAutoResolve: Boolean,
    val suggestedPriority: String,
    val summary: String,
    val category: String,
    val confidence: Double,
    val reasoning: String,
)

data class ArbitrateRequest(
    @field:NotBlank val verdict: String,  // FAVOR_FILER / FAVOR_RESPONDENT / COMPROMISE / INSUFFICIENT_EVIDENCE
    @field:NotBlank val reasoning: String,
    val compensationAmount: String? = null,
)

data class ArbitrationResponse(
    val disputeId: String,
    val verdict: String,
    val reasoning: String,
    val compensationAmount: String?,
    val decidedAt: String,
)

private fun com.commonsengine.dispute.domain.Dispute.toResponse() = DisputeResponse(
    id = id.value,
    transactionId = transactionId,
    filedBy = filedBy,
    filedAgainst = filedAgainst,
    type = type.name,
    priority = priority.name,
    description = description,
    status = status.name,
    createdAt = createdAt.toString(),
    resolution = resolution,
)
