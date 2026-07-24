package com.commonsengine.dispute.service;

import com.commonsengine.dispute.domain.Model.AiScreeningResult;
import com.commonsengine.dispute.domain.Model.ArbitrationVerdict;
import com.commonsengine.dispute.domain.Model.Dispute;
import com.commonsengine.dispute.domain.Model.DisputeId;
import com.commonsengine.dispute.domain.Model.DisputePriority;
import com.commonsengine.dispute.domain.Model.DisputeStatus;
import com.commonsengine.dispute.domain.Model.DisputeType;
import com.commonsengine.dispute.domain.Model.VerdictType;
import com.commonsengine.dispute.infrastructure.persistence.DisputeEntity;
import com.commonsengine.dispute.infrastructure.persistence.DisputeMapper;
import com.commonsengine.dispute.infrastructure.persistence.DisputeRepository;
import com.commonsengine.platform.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 纠纷工单业务服务（#65）
 *
 * 职责：
 * 1. 创建纠纷工单（FILED）并触发 AI 预审
 * 2. AI 预审（置信度阈值决定是否转人工）
 * 3. 人工仲裁（UNDER_REVIEW → ARBITRATING → RESOLVED）
 * 4. 查询工单列表
 *
 * 状态机校验由 Dispute.transitionTo 保证。
 */
@Service
public class DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);

    /** AI 预审置信度阈值——低于此值强制转人工。 */
    private static final double AI_CONFIDENCE_THRESHOLD = 0.7;

    private final DisputeRepository repository;
    private final DisputeMapper mapper;

    public DisputeService(DisputeRepository repository, DisputeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public Dispute fileDispute(String transactionId, String consumerId, String workerId,
                                DisputeType type, DisputePriority priority, String description) {
        Dispute dispute = new Dispute(
                DisputeId.random(),
                transactionId,
                consumerId,
                workerId,
                type,
                priority,
                description,
                DisputeStatus.FILED,
                null,
                null,
                Instant.now(),
                null
        );
        DisputeEntity saved = repository.save(mapper.toEntity(dispute));
        log.info("纠纷工单已创建 id={} type={} priority={}", dispute.getId(), type, priority);
        return mapper.toDomain(saved);
    }

    /**
     * AI 预审——记录结果并决定是否转人工。
     *
     * @return 更新后的 Dispute（状态可能为 UNDER_REVIEW 或 RESOLVED）
     */
    @Transactional
    public Dispute recordAiScreening(DisputeId id, double confidence, String category,
                                      String summary) {
        Dispute dispute = loadOrThrow(id);
        dispute.transitionTo(DisputeStatus.AI_SCREENING);

        AiScreeningResult screening = new AiScreeningResult(confidence, category, summary,
                confidence < AI_CONFIDENCE_THRESHOLD);
        dispute.setAiScreening(screening);

        if (confidence >= AI_CONFIDENCE_THRESHOLD) {
            // 高置信度可直接转人工审核（后续可能自动解决）
            dispute.transitionTo(DisputeStatus.UNDER_REVIEW);
        } else {
            // 低置信度强制人工介入
            dispute.transitionTo(DisputeStatus.UNDER_REVIEW);
        }

        DisputeEntity saved = repository.save(mapper.toEntity(dispute));
        log.info("AI 预审完成 id={} confidence={} needsHuman={}", id, confidence,
                screening.needsHumanReview());
        return mapper.toDomain(saved);
    }

    /**
     * 提交仲裁裁决——状态必须为 UNDER_REVIEW 或 ARBITRATING。
     */
    @Transactional
    public Dispute arbitrate(DisputeId id, VerdictType verdictType, double amount, String reason) {
        Dispute dispute = loadOrThrow(id);

        if (dispute.getStatus() == DisputeStatus.UNDER_REVIEW) {
            dispute.transitionTo(DisputeStatus.ARBITRATING);
        }
        dispute.transitionTo(DisputeStatus.RESOLVED);

        ArbitrationVerdict verdict = new ArbitrationVerdict(verdictType, amount, reason, Instant.now());
        dispute.setVerdict(verdict);
        dispute.setResolvedAt(Instant.now());

        DisputeEntity saved = repository.save(mapper.toEntity(dispute));
        log.info("仲裁裁决完成 id={} verdict={} amount={}", id, verdictType, amount);
        return mapper.toDomain(saved);
    }

    @Transactional(readOnly = true)
    public Dispute findById(DisputeId id) {
        return loadOrThrow(id);
    }

    @Transactional(readOnly = true)
    public List<Dispute> findByStatus(DisputeStatus status) {
        return repository.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Dispute> findByStakeholder(String consumerId, String workerId) {
        return repository.findByStakeholder(consumerId, workerId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private Dispute loadOrThrow(DisputeId id) {
        return repository.findById(id.value())
                .map(mapper::toDomain)
                .orElseThrow(() -> new NotFoundException("Dispute", id.value()));
    }
}
