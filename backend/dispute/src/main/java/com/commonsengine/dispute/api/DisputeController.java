package com.commonsengine.dispute.api;

import com.commonsengine.dispute.domain.Model.DisputePriority;
import com.commonsengine.dispute.domain.Model.DisputeStatus;
import com.commonsengine.dispute.domain.Model.DisputeType;
import com.commonsengine.dispute.domain.Model.VerdictType;
import com.commonsengine.dispute.domain.Model.AiScreeningResult;
import com.commonsengine.dispute.domain.Model.ArbitrationVerdict;
import com.commonsengine.dispute.domain.Model.Dispute;
import com.commonsengine.dispute.domain.Model.DisputeId;
import com.commonsengine.dispute.service.DisputeService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 纠纷工单 REST API（#65）
 *
 * 端点：
 * - POST   /api/v1/dispute                 提交纠纷
 * - GET    /api/v1/dispute/{id}            查询详情
 * - GET    /api/v1/dispute                 按状态/当事人查询
 * - POST   /api/v1/dispute/{id}/ai-screen  记录 AI 预审
 * - POST   /api/v1/dispute/{id}/arbitrate  仲裁
 */
@RestController
@RequestMapping("/api/v1/dispute")
public class DisputeController {

    private final DisputeService service;

    public DisputeController(DisputeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DisputeResponse> file(@RequestBody FileDisputeRequest request) {
        Dispute d = service.fileDispute(
                request.transactionId(),
                request.consumerId(),
                request.workerId(),
                request.type(),
                request.priority(),
                request.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(DisputeResponse.from(d));
    }

    @GetMapping("/{id}")
    public DisputeResponse getById(@PathVariable String id) {
        Dispute d = service.findById(new DisputeId(id));
        return DisputeResponse.from(d);
    }

    @GetMapping
    public List<DisputeResponse> list(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) String consumerId,
            @RequestParam(required = false) String workerId
    ) {
        List<Dispute> disputes;
        if (status != null) {
            disputes = service.findByStatus(status);
        } else if (consumerId != null || workerId != null) {
            disputes = service.findByStakeholder(
                    consumerId != null ? consumerId : "",
                    workerId != null ? workerId : ""
            );
        } else {
            disputes = List.of();
        }
        return disputes.stream().map(DisputeResponse::from).toList();
    }

    @PostMapping("/{id}/ai-screen")
    public AiScreeningResponse recordAiScreening(@PathVariable String id,
                                                  @RequestBody AiScreeningRequest request) {
        Dispute d = service.recordAiScreening(
                new DisputeId(id),
                request.confidence(),
                request.category(),
                request.summary()
        );
        return AiScreeningResponse.from(d);
    }

    @PostMapping("/{id}/arbitrate")
    public ArbitrationResponse arbitrate(@PathVariable String id,
                                          @RequestBody ArbitrateRequest request) {
        Dispute d = service.arbitrate(
                new DisputeId(id),
                request.verdictType(),
                request.amount(),
                request.reason()
        );
        return ArbitrationResponse.from(d);
    }

    // ── DTO records ────────────────────────────────────────

    public record FileDisputeRequest(
            @NotBlank String transactionId,
            @NotBlank String consumerId,
            @NotBlank String workerId,
            @NotNull DisputeType type,
            @NotNull DisputePriority priority,
            @NotBlank String description
    ) {
    }

    public record AiScreeningRequest(
            double confidence,
            String category,
            String summary
    ) {
    }

    public record ArbitrateRequest(
            @NotNull VerdictType verdictType,
            double amount,
            @NotBlank String reason
    ) {
    }

    public record DisputeResponse(
            String id,
            String transactionId,
            String consumerId,
            String workerId,
            DisputeType type,
            DisputePriority priority,
            String description,
            DisputeStatus status,
            AiScreeningResult aiScreening,
            ArbitrationVerdict verdict,
            Instant createdAt,
            Instant resolvedAt
    ) {
        static DisputeResponse from(Dispute d) {
            return new DisputeResponse(
                    d.getId().value(),
                    d.getTransactionId(),
                    d.getConsumerId(),
                    d.getWorkerId(),
                    d.getType(),
                    d.getPriority(),
                    d.getDescription(),
                    d.getStatus(),
                    d.getAiScreening(),
                    d.getVerdict(),
                    d.getCreatedAt(),
                    d.getResolvedAt()
            );
        }
    }

    public record AiScreeningResponse(
            String disputeId,
            DisputeStatus status,
            AiScreeningResult screening
    ) {
        static AiScreeningResponse from(Dispute d) {
            return new AiScreeningResponse(d.getId().value(), d.getStatus(), d.getAiScreening());
        }
    }

    public record ArbitrationResponse(
            String disputeId,
            DisputeStatus status,
            ArbitrationVerdict verdict,
            Instant resolvedAt
    ) {
        static ArbitrationResponse from(Dispute d) {
            return new ArbitrationResponse(d.getId().value(), d.getStatus(), d.getVerdict(), d.getResolvedAt());
        }
    }
}
