package com.commonsengine.payment.api

import com.commonsengine.payment.domain.Transaction
import com.commonsengine.payment.domain.TransactionId
import com.commonsengine.payment.service.PaymentService
import com.commonsengine.platform.exception.NotFoundException
import com.commonsengine.platform.support.Enums
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/**
 * 支付分账 REST API
 *
 * 核心流程：发起收款 → 分账结算 → 退款 → 查询账本流水
 * 所有资金流向透明可审计（章程第 4.3 条）。
 *
 * 注意：Transaction 对象不单独持久化（仅事件溯源），因此 settle/refund
 * 端点需要从 charge 响应中回传交易信息。
 */
@RestController
@RequestMapping("/api/v1/payment")
open class PaymentController(
    private val service: PaymentService,
) {

    /** 发起收款——创建交易并执行收款，返回已收款交易 */
    @PostMapping("/charge")
    fun charge(@Valid @RequestBody body: ChargeRequest): TransactionResponse {
        val tx = Transaction(
            id = TransactionId.random(),
            consumerId = body.consumerId,
            workerId = body.workerId,
            amount = body.amount,
            serviceType = body.serviceType,
        )
        val charged = service.charge(tx)
        return charged.toResponse()
    }

    /**
     * Settle - load authoritative transaction from event store.
     *
     * Transaction fields (amount, workerId) are no longer accepted from
     * the request body. The service reconstructs them from the
     * CHARGE_CREATED event, preventing payment-integrity violations.
     *
     * Settlement rules are resolved server-side (currently DEFAULT);
     * client-supplied rate overrides have been removed to prevent
     * governance bypass.
     *
     * @param transactionId from charge response
     */
    @PostMapping("/{transactionId}/settle")
    fun settle(
        @PathVariable transactionId: String,
    ): SettlementResponse {
        val tx = service.findById(TransactionId(transactionId))
            ?: throw NotFoundException("Transaction", transactionId)

        val result = service.settle(tx)
        return SettlementResponse(
            transactionId = result.transactionId.value,
            totalAmount = result.totalAmount.toString(),
            workerPayout = result.workerPayout.toString(),
            platformFee = result.platformFee.toString(),
            commonsFund = result.commonsFund.toString(),
            workerShareRate = result.rule.workerShareRate.toString(),
            breakdown = result.breakdown,
        )
    }

    /**
     * Refund - load authoritative transaction from event store.
     *
     * Amount is reconstructed from the CHARGE_CREATED event to ensure
     * refund amount matches the original charge.
     */
    @PostMapping("/{transactionId}/refund")
    fun refund(
        @PathVariable transactionId: String,
        @Valid @RequestBody body: RefundRequest,
    ): RefundResponse {
        val tx = service.findById(TransactionId(transactionId))
            ?: throw NotFoundException("Transaction", transactionId)

        val success = service.refund(tx, body.reason)
        return RefundResponse(success = success, transactionId = transactionId)
    }

    /** 查询交易流水（公开审计——章程第 4.3 条） */
    @GetMapping("/{transactionId}/history")
    fun getHistory(@PathVariable transactionId: String): List<Map<String, Any?>> {
        val events = service.getTransactionHistory(TransactionId(transactionId))
        return events.map { event ->
            when (event) {
                is com.commonsengine.payment.domain.LedgerEvent.ChargeCreated -> mapOf(
                    "type" to "CHARGE_CREATED",
                    "eventId" to event.eventId,
                    "transactionId" to event.transactionId.value,
                    "timestamp" to event.timestamp.toString(),
                    "consumerId" to event.consumerId,
                    "workerId" to event.workerId,
                    "amount" to event.amount.toString(),
                    "serviceType" to event.serviceType,
                    "paymentChannel" to event.paymentChannel,
                )
                is com.commonsengine.payment.domain.LedgerEvent.SettlementCompleted -> mapOf(
                    "type" to "SETTLEMENT_COMPLETED",
                    "eventId" to event.eventId,
                    "transactionId" to event.transactionId.value,
                    "timestamp" to event.timestamp.toString(),
                    "workerPayout" to event.workerPayout.toString(),
                    "platformFee" to event.platformFee.toString(),
                    "commonsFund" to event.commonsFund.toString(),
                )
                is com.commonsengine.payment.domain.LedgerEvent.RefundIssued -> mapOf(
                    "type" to "REFUND_ISSUED",
                    "eventId" to event.eventId,
                    "transactionId" to event.transactionId.value,
                    "timestamp" to event.timestamp.toString(),
                    "refundAmount" to event.refundAmount.toString(),
                    "reason" to event.reason,
                )
            }
        }
    }
}

// ── DTO ──────────────────────────────────────────────────────────

data class ChargeRequest(
    @field:NotBlank val consumerId: String,
    @field:NotBlank val workerId: String,
    @field:Positive val amount: BigDecimal,
    @field:NotBlank val serviceType: String,
)

data class RefundRequest(
    @field:NotBlank val reason: String,
)

data class TransactionResponse(
    val id: String,
    val consumerId: String,
    val workerId: String,
    val amount: String,
    val serviceType: String,
    val status: String,
)

data class RefundResponse(
    val success: Boolean,
    val transactionId: String,
)

data class SettlementResponse(
    val transactionId: String,
    val totalAmount: String,
    val workerPayout: String,
    val platformFee: String,
    val commonsFund: String,
    val workerShareRate: String,
    val breakdown: String,
)

private fun Transaction.toResponse() = TransactionResponse(
    id = id.value,
    consumerId = consumerId,
    workerId = workerId,
    amount = amount.toString(),
    serviceType = serviceType,
    status = status.name,
)
