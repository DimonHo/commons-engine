package com.commonsengine.rating.infrastructure.persistence

import com.commonsengine.rating.domain.Rating
import com.commonsengine.rating.domain.RatingDirection
import com.commonsengine.rating.domain.RatingId
import com.commonsengine.rating.domain.RatingTag
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 评价 JPA 实体
 *
 * 双向评价（消费者↔劳动者），反惩罚性设计——评价不挂钩接单资格。
 * tags: Set<RatingTag> 以分号分隔的字符串存储。
 */
@Entity
@Table(name = "ratings")
class RatingEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "rating_id", nullable = false, length = 36)
    val ratingId: String,

    @Column(name = "transaction_id", nullable = false, length = 36)
    val transactionId: String,

    @Column(name = "rater_id", nullable = false, length = 36)
    val raterId: String,

    @Column(name = "ratee_id", nullable = false, length = 36)
    val rateeId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 30)
    val direction: RatingDirection,

    @Column(name = "score", nullable = false)
    val score: Int,

    /** 标签以分号分隔存储，如 "POLITE;PUNCTUAL" */
    @Column(name = "tags")
    val tags: String? = null,

    @Column(name = "comment", columnDefinition = "TEXT")
    val comment: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
)

// ── Entity ↔ Domain 映射 ──────────────────────────────

/** Entity → 领域模型 */
fun RatingEntity.toDomain(): Rating = Rating(
    id = RatingId(ratingId),
    transactionId = transactionId,
    raterId = raterId,
    rateeId = rateeId,
    direction = direction,
    score = score,
    tags = tags?.let { parseTags(it) } ?: emptySet(),
    comment = comment,
    createdAt = createdAt,
)

/** 领域模型 → Entity */
fun Rating.toEntity(): RatingEntity = RatingEntity(
    ratingId = id.value,
    transactionId = transactionId,
    raterId = raterId,
    rateeId = rateeId,
    direction = direction,
    score = score,
    tags = if (tags.isEmpty()) null else tags.joinToString(";") { it.name },
    comment = comment,
    createdAt = createdAt,
)

/** 解析分号分隔的标签字符串 */
private fun parseTags(s: String): Set<RatingTag> =
    s.split(";")
        .filter { it.isNotBlank() }
        .mapNotNull { name -> runCatching { RatingTag.valueOf(name.trim()) }.getOrNull() }
        .toSet()
