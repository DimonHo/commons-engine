package com.commonsengine.rating.service;

import com.commonsengine.rating.domain.Model.CreditProfile;
import com.commonsengine.rating.domain.Model.Rating;
import com.commonsengine.rating.domain.Model.RatingTag;
import com.commonsengine.rating.infrastructure.persistence.RatingMapper;
import com.commonsengine.rating.infrastructure.persistence.RatingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 信用评价服务。
 *
 * <p>核心原则：
 * <ol>
 *   <li>双向评价——劳动者也能评价消费者</li>
 *   <li>反惩罚——评价不挂钩接单资格，仅作参考</li>
 *   <li>数据可携带——劳动者可导出自己的信用记录</li>
 * </ol>
 *
 * <p>持久化：使用 JPA + PostgreSQL，重启不丢数据。
 */
@Service
public class RatingService {

    private final RatingRepository repository;

    public RatingService(RatingRepository repository) {
        this.repository = repository;
    }

    /** 提交评价 */
    @Transactional
    public Rating submit(Rating rating) {
        repository.save(RatingMapper.toEntity(rating));
        return rating;
    }

    /** 查询某人收到的所有评价 */
    @Transactional(readOnly = true)
    public List<Rating> findReceived(String memberId) {
        return repository.findByRateeId(memberId).stream()
                .map(RatingMapper::toDomain)
                .toList();
    }

    /** 查询某人发出的所有评价 */
    @Transactional(readOnly = true)
    public List<Rating> findGiven(String memberId) {
        return repository.findByRaterId(memberId).stream()
                .map(RatingMapper::toDomain)
                .toList();
    }

    /** 查询某笔交易的评价（双方向） */
    @Transactional(readOnly = true)
    public List<Rating> findByTransaction(String transactionId) {
        return repository.findByTransactionId(transactionId).stream()
                .map(RatingMapper::toDomain)
                .toList();
    }

    /**
     * 聚合信用画像。
     */
    @Transactional(readOnly = true)
    public CreditProfile getCreditProfile(String memberId) {
        List<Rating> received = findReceived(memberId);
        if (received.isEmpty()) {
            return new CreditProfile(memberId, 5.0, 0, Map.of(), List.of());
        }

        List<Integer> scores = received.stream().map(Rating::score).toList();
        double avg = scores.stream().mapToInt(Integer::intValue).average().orElse(5.0);

        Map<RatingTag, Integer> tagFreq = new EnumMap<>(RatingTag.class);
        for (Rating r : received) {
            for (RatingTag tag : r.tags()) {
                tagFreq.merge(tag, 1, Integer::sum);
            }
        }

        // 最近 20 条评分
        List<Integer> recent = new ArrayList<>(scores);
        int from = Math.max(0, recent.size() - 20);
        List<Integer> recentScores = recent.subList(from, recent.size());

        return new CreditProfile(memberId, avg, received.size(), tagFreq, recentScores);
    }

    /**
     * 导出信用记录（数据归个人——劳动者可带走）。
     */
    @Transactional(readOnly = true)
    public String exportProfile(String memberId) {
        CreditProfile profile = getCreditProfile(memberId);
        List<Rating> received = findReceived(memberId);

        StringBuilder sb = new StringBuilder();
        sb.append("=== 公地引擎 · 信用记录导出 ===\n");
        sb.append("成员 ID: ").append(profile.memberId()).append('\n');
        sb.append("平均评分: ").append(String.format("%.2f", profile.averageScore())).append('\n');
        sb.append("评价总数: ").append(profile.totalRatings()).append('\n');
        sb.append('\n');
        sb.append("标签统计:\n");
        profile.tagFrequency().forEach((tag, count) ->
                sb.append("  ").append(tag.name()).append(": ").append(count).append(" 次\n"));
        sb.append('\n');
        sb.append("评价明细:\n");
        for (Rating r : received) {
            String tags = r.tags().stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
            sb.append("  [").append(r.createdAt())
                    .append("] 分数:").append(r.score())
                    .append(" 标签:").append(tags)
                    .append(' ').append(r.comment() == null ? "" : r.comment())
                    .append('\n');
        }
        sb.append('\n');
        sb.append("注：此信用记录归您个人所有，可携带至其他合作社。\n");
        return sb.toString();
    }
}
