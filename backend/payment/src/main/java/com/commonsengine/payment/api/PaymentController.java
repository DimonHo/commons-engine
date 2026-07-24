package com.commonsengine.payment.api;

import com.commonsengine.payment.domain.Model.LedgerEvent;
import com.commonsengine.payment.domain.Model.Transaction;
import com.commonsengine.payment.domain.Model.TransactionId;
import com.commonsengine.payment.service.PaymentService;
import com.commonsengine.platform.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付分账 REST API。
 *
 * <p>核心流程：发起收款 → 分账结算 → 退款 → 查询账本流水。
 * 所有资金流向透明可审计（章程第 4.3 条）。
 *
 * <p>注意：Transaction 对象不单独持久化（仅事件溯源），因此 settle/refund
 * 端点通过 {@code transactionId} 从事件存储重建交易。
 */
@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** 发起收款——创建交易并执行收款，返回已收款交易 */
    @PostMapping("/charge")
    public TransactionResponse charge(@Valid @RequestBody ChargeRequest body) {
        Transaction tx = new Transaction(
                TransactionId.random(),
                body.consumerId(),
                body.workerId(),
                body.amount(),
                body.serviceType(),
                java.time.Instant.now(),
                null
        );
        Transaction charged = service.charge(tx);
        return toResponse(charged);
    }

    /**
     * Settle——从事件存储加载权威交易后分账。
     *
     * <p>交易字段（金额、workerId）不再从请求体接收。服务端通过 CHARGE_CREATED
     * 事件重建，防止支付完整性违规。分账规则由服务端解析（当前为 DEFAULT），
     * 客户端提供的比例覆盖已移除，防止治理绕过。
     *
     * @param transactionId 来自 charge 响应的交易 ID
     */
    @PostMapping("/{transactionId}/settle")
    public SettlementResponse settle(@PathVariable String transactionId) {
        Transaction tx = service.findById(new TransactionId(transactionId))
                .orElseThrow(() -> new NotFoundException("Transaction", transactionId));

        var result = service.settle(tx);
        return new SettlementResponse(
                result.transactionId().value(),
                result.totalAmount().toString(),
                result.workerPayout().toString(),
                result.platformFee().toString(),
                result.commonsFund().toString(),
                result.rule().workerShareRate().toString(),
                result.breakdown()
        );
    }

    /**
     * Refund——从事件存储加载权威交易后退款。
     *
     * <p>退款金额通过 CHARGE_CREATED 事件重建，确保退款金额与原始收款一致。
     */
    @PostMapping("/{transactionId}/refund")
    public RefundResponse refund(@PathVariable String transactionId,
                                 @Valid @RequestBody RefundRequest body) {
        Transaction tx = service.findById(new TransactionId(transactionId))
                .orElseThrow(() -> new NotFoundException("Transaction", transactionId));

        boolean success = service.refund(tx, body.reason());
        return new RefundResponse(success, transactionId);
    }

    /** 查询交易流水（公开审计——章程第 4.3 条） */
    @GetMapping("/{transactionId}/history")
    public List<Map<String, Object>> getHistory(@PathVariable String transactionId) {
        List<LedgerEvent> events = service.getTransactionHistory(new TransactionId(transactionId));
        return events.stream().map(PaymentController::eventToMap).toList();
    }

    // ── 事件 → Map（审计视图） ──────────────────────────

    private static Map<String, Object> eventToMap(LedgerEvent event) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (event instanceof LedgerEvent.ChargeCreated c) {
            m.put("type", "CHARGE_CREATED");
            m.put("eventId", c.eventId());
            m.put("transactionId", c.transactionId().value());
            m.put("timestamp", c.timestamp().toString());
            m.put("consumerId", c.consumerId());
            m.put("workerId", c.workerId());
            m.put("amount", c.amount().toString());
            m.put("serviceType", c.serviceType());
            m.put("paymentChannel", c.paymentChannel());
        } else if (event instanceof LedgerEvent.SettlementCompleted s) {
            m.put("type", "SETTLEMENT_COMPLETED");
            m.put("eventId", s.eventId());
            m.put("transactionId", s.transactionId().value());
            m.put("timestamp", s.timestamp().toString());
            m.put("workerPayout", s.workerPayout().toString());
            m.put("platformFee", s.platformFee().toString());
            m.put("commonsFund", s.commonsFund().toString());
        } else if (event instanceof LedgerEvent.RefundIssued r) {
            m.put("type", "REFUND_ISSUED");
            m.put("eventId", r.eventId());
            m.put("transactionId", r.transactionId().value());
            m.put("timestamp", r.timestamp().toString());
            m.put("refundAmount", r.refundAmount().toString());
            m.put("reason", r.reason());
        }
        return m;
    }

    // ── Domain → Response 转换 ──────────────────────────

    private static TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.id().value(),
                t.consumerId(),
                t.workerId(),
                t.amount().toString(),
                t.serviceType(),
                t.status().name()
        );
    }

    // ── DTO ──────────────────────────────────────────────

    public record ChargeRequest(
            @NotBlank String consumerId,
            @NotBlank String workerId,
            @Positive BigDecimal amount,
            @NotBlank String serviceType
    ) {
    }

    public record RefundRequest(
            @NotBlank String reason
    ) {
    }

    public record TransactionResponse(
            String id,
            String consumerId,
            String workerId,
            String amount,
            String serviceType,
            String status
    ) {
    }

    public record RefundResponse(
            boolean success,
            String transactionId
    ) {
    }

    public record SettlementResponse(
            String transactionId,
            String totalAmount,
            String workerPayout,
            String platformFee,
            String commonsFund,
            String workerShareRate,
            String breakdown
    ) {
    }
}
