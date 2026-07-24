package com.commonsengine.dispute.service;

import com.commonsengine.dispute.domain.Model.ArbitrationVerdict;
import com.commonsengine.dispute.domain.Model.Dispute;
import com.commonsengine.dispute.domain.Model.DisputeId;
import com.commonsengine.dispute.domain.Model.DisputePriority;
import com.commonsengine.dispute.domain.Model.DisputeStatus;
import com.commonsengine.dispute.domain.Model.DisputeType;
import com.commonsengine.dispute.domain.Model.VerdictType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纠纷工单服务测试——从 Kotlin DisputeServiceTest 转换。
 *
 * <p>适配 Java DisputeService API：
 * <ul>
 *   <li>{@code fileDispute(tx, consumer, worker, type, priority, description)}</li>
 *   <li>{@code recordAiScreening(id, confidence, category, summary)}</li>
 *   <li>{@code arbitrate(id, verdictType, amount, reason)}</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DisputeServiceTest {

    @Autowired
    private DisputeService service;

    @Test
    void fileCreatesDisputeInFiledStatus() {
        Dispute d = service.fileDispute("tx-1", "c1", "w1",
                DisputeType.PAYMENT_DISPUTE, DisputePriority.MEDIUM, "多收了钱");
        assertEquals(DisputeStatus.FILED, d.getStatus());
        assertNotNull(d.getId());
    }

    @Test
    void aiScreeningAssignsHighPriorityForBehavioralIssues() {
        Dispute d = service.fileDispute("tx-1", "c1", "w1",
                DisputeType.SERVICE_QUALITY, DisputePriority.HIGH, "司机辱骂乘客");
        Dispute result = service.recordAiScreening(d.getId(), 0.95, "behavioral", "行为问题需人工调查");

        // 高置信度 → UNDER_REVIEW（转人工审核）
        assertEquals(DisputeStatus.UNDER_REVIEW, result.getStatus());
        assertNotNull(result.getAiScreening());
        assertTrue(result.getAiScreening().summary().contains("人工调查"));
    }

    @Test
    void aiScreeningWithLowConfidenceForcesHumanReview() {
        Dispute d = service.fileDispute("tx-1", "c1", "w1",
                DisputeType.SERVICE_QUALITY, DisputePriority.LOW, "司机迟到");
        Dispute result = service.recordAiScreening(d.getId(), 0.3, "service_quality", "置信度低");

        // 低置信度强制人工介入
        assertEquals(DisputeStatus.UNDER_REVIEW, result.getStatus());
        assertTrue(result.getAiScreening().needsHumanReview());
    }

    @Test
    void arbitrateResolvesDisputeWithVerdict() {
        Dispute d = service.fileDispute("tx-1", "c1", "w1",
                DisputeType.PAYMENT_DISPUTE, DisputePriority.MEDIUM, "多收费");
        service.recordAiScreening(d.getId(), 0.9, "fare", "预审通过");

        Dispute resolved = service.arbitrate(
                d.getId(),
                VerdictType.REFUND_PARTIAL,
                new BigDecimal("10.00").doubleValue(),
                "经查证，确有多收 10 元的情况，支持投诉方。"
        );

        assertEquals(DisputeStatus.RESOLVED, resolved.getStatus());
        ArbitrationVerdict verdict = resolved.getVerdict();
        assertNotNull(verdict);
        assertEquals(VerdictType.REFUND_PARTIAL, verdict.type());
        assertNotNull(resolved.getResolvedAt());
    }

    @Test
    void cannotArbitrateAlreadyResolvedDispute() {
        Dispute d = service.fileDispute("tx-1", "c1", "w1",
                DisputeType.PAYMENT_DISPUTE, DisputePriority.MEDIUM, "多收费");
        service.recordAiScreening(d.getId(), 0.9, "fare", "预审");
        service.arbitrate(d.getId(), VerdictType.REFUND_PARTIAL, 10.0, "理由");

        // 已 RESOLVED 的工单不能再次仲裁
        assertThrows(Exception.class, () ->
                service.arbitrate(d.getId(), VerdictType.REWORK, 0.0, "再次裁决"));
    }

    @Test
    void findByStatusFiltersCorrectly() {
        Dispute d1 = service.fileDispute("tx-1", "c1", "w1",
                DisputeType.PAYMENT_DISPUTE, DisputePriority.MEDIUM, "投诉1");
        service.fileDispute("tx-2", "c2", "w2",
                DisputeType.SERVICE_QUALITY, DisputePriority.HIGH, "投诉2");
        // d2 进入 AI_SCREENING → UNDER_REVIEW
        service.recordAiScreening(d1.getId(), 0.9, "fare", "ok");

        List<Dispute> filed = service.findByStatus(DisputeStatus.FILED);
        assertEquals(1, filed.size());
    }

    @Test
    void findByIdReturnsPersistedDispute() {
        Dispute d = service.fileDispute("tx-1", "c1", "w1",
                DisputeType.PAYMENT_DISPUTE, DisputePriority.MEDIUM, "多收费");

        Dispute found = service.findById(d.getId());
        assertNotNull(found);
        assertEquals(d.getId(), found.getId());
        assertEquals("多收费", found.getDescription());
    }

    @Test
    void findByStakeholderReturnsRelevantDisputes() {
        service.fileDispute("tx-1", "c1", "w1",
                DisputeType.PAYMENT_DISPUTE, DisputePriority.MEDIUM, "投诉1");
        service.fileDispute("tx-2", "c1", "w2",
                DisputeType.SERVICE_QUALITY, DisputePriority.LOW, "投诉2");

        List<Dispute> forConsumer = service.findByStakeholder("c1", null);
        assertTrue(forConsumer.size() >= 2);
    }

    @Test
    void findAllViaFindByStatusCoversStatuses() {
        service.fileDispute("tx-1", "c1", "w1",
                DisputeType.PAYMENT_DISPUTE, DisputePriority.MEDIUM, "投诉1");
        service.fileDispute("tx-2", "c2", "w2",
                DisputeType.SERVICE_QUALITY, DisputePriority.HIGH, "投诉2");

        List<Dispute> filed = service.findByStatus(DisputeStatus.FILED);
        assertEquals(2, filed.size());
    }
}
