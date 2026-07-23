package com.commonsengine.dispute.infrastructure.persistence;

import com.commonsengine.dispute.domain.Model.AiScreeningResult;
import com.commonsengine.dispute.domain.Model.ArbitrationVerdict;
import com.commonsengine.dispute.domain.Model.Dispute;
import com.commonsengine.dispute.domain.Model.DisputeId;
import com.commonsengine.dispute.domain.Model.DisputePriority;
import com.commonsengine.dispute.domain.Model.DisputeStatus;
import com.commonsengine.dispute.domain.Model.DisputeType;
import com.commonsengine.dispute.domain.Model.VerdictType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Dispute ↔ DisputeEntity 双向映射器（Kotlin extension → Java static Mapper）。
 *
 * JSON 列（ai_screening / verdict）通过注入的 ObjectMapper 序列化/反序列化，
 * 容错策略：解析失败不抛异常，返回 null（视为尚未预审/仲裁）。
 */
@Component
public final class DisputeMapper {

    private final ObjectMapper objectMapper;

    public DisputeMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Dispute toDomain(DisputeEntity e) {
        if (e == null) {
            return null;
        }
        return new Dispute(
                new DisputeId(e.getId()),
                e.getTransactionId(),
                e.getConsumerId(),
                e.getWorkerId(),
                e.getType(),
                e.getPriority(),
                e.getDescription(),
                e.getStatus(),
                parseJson(e.getAiScreeningJson(), AiScreeningResult.class),
                parseVerdict(e.getVerdictJson()),
                e.getCreatedAt(),
                e.getResolvedAt()
        );
    }

    public DisputeEntity toEntity(Dispute d) {
        DisputeEntity e = new DisputeEntity();
        e.setId(d.getId().value());
        e.setTransactionId(d.getTransactionId());
        e.setConsumerId(d.getConsumerId());
        e.setWorkerId(d.getWorkerId());
        e.setType(d.getType());
        e.setPriority(d.getPriority());
        e.setDescription(d.getDescription());
        e.setStatus(d.getStatus());
        e.setAiScreeningJson(toJson(d.getAiScreening()));
        e.setVerdictJson(toJson(d.getVerdict()));
        e.setCreatedAt(d.getCreatedAt());
        e.setResolvedAt(d.getResolvedAt());
        return e;
    }

    private <T> T parseJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return null;
        }
    }

    private ArbitrationVerdict parseVerdict(String json) {
        return parseJson(json, ArbitrationVerdict.class);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
