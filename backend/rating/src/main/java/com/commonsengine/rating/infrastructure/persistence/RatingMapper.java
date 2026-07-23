package com.commonsengine.rating.infrastructure.persistence;

import com.commonsengine.rating.domain.Model.Rating;
import com.commonsengine.rating.domain.Model.RatingId;
import com.commonsengine.rating.domain.Model.RatingTag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rating entity ↔ domain 映射器。
 */
public final class RatingMapper {

    private RatingMapper() {
    }

    /** Entity → 领域模型 */
    public static Rating toDomain(RatingEntity e) {
        return new Rating(
                new RatingId(e.getRatingId()),
                e.getTransactionId(),
                e.getRaterId(),
                e.getRateeId(),
                e.getDirection(),
                e.getScore(),
                parseTags(e.getTags()),
                e.getComment(),
                e.getCreatedAt()
        );
    }

    /** 领域模型 → Entity（新建） */
    public static RatingEntity toEntity(Rating r) {
        RatingEntity e = new RatingEntity();
        e.setRatingId(r.id().value());
        e.setTransactionId(r.transactionId());
        e.setRaterId(r.raterId());
        e.setRateeId(r.rateeId());
        e.setDirection(r.direction());
        e.setScore(r.score());
        e.setTags(r.tags().isEmpty() ? null
                : r.tags().stream().map(Enum::name).collect(Collectors.joining(";")));
        e.setComment(r.comment());
        e.setCreatedAt(r.createdAt());
        return e;
    }

    /** 解析分号分隔的标签字符串（非法标签静默跳过，与 Kotlin 版 runCatching.getOrNull() 等价） */
    static Set<RatingTag> parseTags(String s) {
        if (s == null || s.isBlank()) {
            return Set.of();
        }
        Set<RatingTag> result = new LinkedHashSet<>();
        for (String part : s.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            try {
                result.add(RatingTag.valueOf(trimmed));
            } catch (IllegalArgumentException ignored) {
                // 静默跳过未知标签
            }
        }
        return result;
    }
}
