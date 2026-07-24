package com.commonsengine.dispute.infrastructure.persistence;

import com.commonsengine.dispute.domain.Model.AiScreeningResult;
import com.commonsengine.dispute.domain.Model.ArbitrationVerdict;
import com.commonsengine.dispute.domain.Model.Dispute;
import com.commonsengine.dispute.domain.Model.DisputeId;
import com.commonsengine.dispute.domain.Model.DisputePriority;
import com.commonsengine.dispute.domain.Model.DisputeStatus;
import com.commonsengine.dispute.domain.Model.DisputeType;
import com.commonsengine.dispute.domain.Model.VerdictType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Dispute JPA 实体——与 disputes 表对应。
 *
 * @Entity → 普通 Java 类，无参构造 + getter/setter。
 * 嵌套值对象（AiScreeningResult / ArbitrationVerdict）以 JSON 字符串列存储。
 */
@Entity
@Table(name = "disputes")
public class DisputeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private String id;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "consumer_id", nullable = false)
    private String consumerId;

    @Column(name = "worker_id", nullable = false)
    private String workerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private DisputeType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private DisputePriority priority;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DisputeStatus status;

    @Column(name = "ai_screening", columnDefinition = "jsonb")
    private String aiScreeningJson;

    @Column(name = "verdict", columnDefinition = "jsonb")
    private String verdictJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public DisputeEntity() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public DisputeType getType() {
        return type;
    }

    public void setType(DisputeType type) {
        this.type = type;
    }

    public DisputePriority getPriority() {
        return priority;
    }

    public void setPriority(DisputePriority priority) {
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public void setStatus(DisputeStatus status) {
        this.status = status;
    }

    public String getAiScreeningJson() {
        return aiScreeningJson;
    }

    public void setAiScreeningJson(String aiScreeningJson) {
        this.aiScreeningJson = aiScreeningJson;
    }

    public String getVerdictJson() {
        return verdictJson;
    }

    public void setVerdictJson(String verdictJson) {
        this.verdictJson = verdictJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
