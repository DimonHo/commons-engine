package com.commonsengine.rating.infrastructure.persistence;

import com.commonsengine.rating.domain.Model.RatingDirection;
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
 * 评价 JPA 实体。
 *
 * <p>双向评价（消费者↔劳动者），反惩罚性设计——评价不挂钩接单资格。
 * tags 以分号分隔的字符串存储，如 "POLITE;PUNCTUAL"。
 *
 * <p>JPA 要求无参构造器与可变字段，故使用普通 Java 类。
 */
@Entity
@Table(name = "ratings")
public class RatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rating_id", nullable = false, length = 36)
    private String ratingId;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Column(name = "rater_id", nullable = false, length = 36)
    private String raterId;

    @Column(name = "ratee_id", nullable = false, length = 36)
    private String rateeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 30)
    private RatingDirection direction;

    @Column(name = "score", nullable = false)
    private int score;

    /** 标签以分号分隔存储，如 "POLITE;PUNCTUAL" */
    @Column(name = "tags")
    private String tags;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** JPA 要求的无参构造器 */
    public RatingEntity() {
    }

    // ── getters / setters ──────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRatingId() {
        return ratingId;
    }

    public void setRatingId(String ratingId) {
        this.ratingId = ratingId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getRaterId() {
        return raterId;
    }

    public void setRaterId(String raterId) {
        this.raterId = raterId;
    }

    public String getRateeId() {
        return rateeId;
    }

    public void setRateeId(String rateeId) {
        this.rateeId = rateeId;
    }

    public RatingDirection getDirection() {
        return direction;
    }

    public void setDirection(RatingDirection direction) {
        this.direction = direction;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
