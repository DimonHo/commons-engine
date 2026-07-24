package com.commonsengine.rating.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 信用评价领域模型（rating 模块）。
 *
 * <p>核心原则：双向评价（打破单向权力）+ 反惩罚性设计（评价不挂钩接单资格）+ 数据可携带。
 */
public final class Model {

    private Model() {
    }

    /**
     * 评价方向——双向评价，打破单向权力。
     */
    public enum RatingDirection {
        /** 劳动者评价消费者 */
        WORKER_TO_CONSUMER,
        /** 消费者评价劳动者 */
        CONSUMER_TO_WORKER,
    }

    /**
     * 评价标签——标准化评价维度，避免恶意低分。
     */
    public enum RatingTag {
        /** 礼貌 */
        POLITE,
        /** 守时 */
        PUNCTUAL,
        /** 专业 */
        PROFESSIONAL,
        /** 安全驾驶 */
        SAFE_DRIVING,
        /** 沟通顺畅 */
        GOOD_COMMUNICATION,
        /** 整洁 */
        CLEAN,
        /** 耐心 */
        PATIENT,
        /** 公平 */
        FAIR,
    }

    /**
     * 评价 ID（UUID 字符串）。
     */
    public record RatingId(String value) {
        public RatingId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("RatingId 不能为空");
            }
        }

        public static RatingId random() {
            return new RatingId(UUID.randomUUID().toString());
        }
    }

    /**
     * 单条评价。
     *
     * <p>反惩罚性设计（章程第 3.3 条）：
     * <ul>
     *   <li>评价不直接挂钩接单资格（不自动降权或停派）</li>
     *   <li>仅作为参考信息</li>
     * </ul>
     *
     * <p>因含评分校验逻辑（score 必须在 1-5），使用普通类（非 record）。
     */
    public static final class Rating {

        private final RatingId id;
        private final String transactionId;
        private final String raterId;
        private final String rateeId;
        private final RatingDirection direction;
        private final int score;
        private final Set<RatingTag> tags;
        private final String comment;
        private final Instant createdAt;

        public Rating(RatingId id, String transactionId, String raterId, String rateeId,
                      RatingDirection direction, int score,
                      Set<RatingTag> tags, String comment, Instant createdAt) {
            if (score < 1 || score > 5) {
                throw new IllegalArgumentException("评分必须在 1-5 范围内，实际: " + score);
            }
            this.id = id;
            this.transactionId = transactionId;
            this.raterId = raterId;
            this.rateeId = rateeId;
            this.direction = direction;
            this.score = score;
            this.tags = tags == null ? Set.of() : Set.copyOf(tags);
            this.comment = comment;
            this.createdAt = createdAt == null ? Instant.now() : createdAt;
        }

        public RatingId id() {
            return id;
        }

        public String transactionId() {
            return transactionId;
        }

        public String raterId() {
            return raterId;
        }

        public String rateeId() {
            return rateeId;
        }

        public RatingDirection direction() {
            return direction;
        }

        public int score() {
            return score;
        }

        public Set<RatingTag> tags() {
            return tags;
        }

        public String comment() {
            return comment;
        }

        public Instant createdAt() {
            return createdAt;
        }
    }

    /**
     * 信用画像——聚合某人的所有评价。
     *
     * <p>劳动者离开合作社时可以带走自己的信用记录（数据归个人）。
     *
     * @param memberId       成员 ID
     * @param averageScore   平均评分
     * @param totalRatings   评价总数
     * @param tagFrequency   各标签出现次数
     * @param recentScores   最近 N 条评分
     */
    public record CreditProfile(
            String memberId,
            double averageScore,
            int totalRatings,
            Map<RatingTag, Integer> tagFrequency,
            java.util.List<Integer> recentScores
    ) {
    }
}
