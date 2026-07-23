package com.commonsengine.rating.api;

import com.commonsengine.platform.support.EnumParser;
import com.commonsengine.rating.domain.Model.Rating;
import com.commonsengine.rating.domain.Model.RatingDirection;
import com.commonsengine.rating.domain.Model.RatingId;
import com.commonsengine.rating.domain.Model.RatingTag;
import com.commonsengine.rating.service.RatingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信用评价 REST API。
 *
 * <p>核心原则：双向评价 + 反惩罚设计（评价不挂钩接单资格）+ 数据可携带。
 */
@RestController
@RequestMapping("/api/v1/rating")
public class RatingController {

    private final RatingService service;

    public RatingController(RatingService service) {
        this.service = service;
    }

    /** 提交评价 */
    @PostMapping("/submit")
    public RatingResponse submit(@Valid @RequestBody SubmitRatingRequest body) {
        Rating rating = new Rating(
                RatingId.random(),
                body.transactionId(),
                body.raterId(),
                body.rateeId(),
                EnumParser.parse(body.direction(), RatingDirection.class),
                body.score(),
                EnumParser.parseAll(body.tags(), RatingTag.class),
                body.comment(),
                java.time.Instant.now()
        );
        Rating saved = service.submit(rating);
        return toResponse(saved);
    }

    /** 查询某人收到的评价 */
    @GetMapping("/received/{memberId}")
    public List<RatingResponse> findReceived(@PathVariable String memberId) {
        return service.findReceived(memberId).stream()
                .map(RatingController::toResponse)
                .toList();
    }

    /** 查询某人发出的评价 */
    @GetMapping("/given/{memberId}")
    public List<RatingResponse> findGiven(@PathVariable String memberId) {
        return service.findGiven(memberId).stream()
                .map(RatingController::toResponse)
                .toList();
    }

    /** 查询某笔交易的评价 */
    @GetMapping("/transaction/{transactionId}")
    public List<RatingResponse> findByTransaction(@PathVariable String transactionId) {
        return service.findByTransaction(transactionId).stream()
                .map(RatingController::toResponse)
                .toList();
    }

    /** 聚合信用画像 */
    @GetMapping("/profile/{memberId}")
    public CreditProfileResponse getCreditProfile(@PathVariable String memberId) {
        var profile = service.getCreditProfile(memberId);
        Map<String, Integer> tagFrequency = new LinkedHashMap<>();
        profile.tagFrequency().forEach((tag, count) -> tagFrequency.put(tag.name(), count));
        return new CreditProfileResponse(
                profile.memberId(),
                profile.averageScore(),
                profile.totalRatings(),
                tagFrequency,
                profile.recentScores()
        );
    }

    /** 导出信用记录（数据归个人——劳动者可带走） */
    @GetMapping(value = "/export/{memberId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportProfile(@PathVariable String memberId) {
        return service.exportProfile(memberId);
    }

    // ── Domain → Response 转换 ──────────────────────────

    private static RatingResponse toResponse(Rating r) {
        return new RatingResponse(
                r.id().value(),
                r.transactionId(),
                r.raterId(),
                r.rateeId(),
                r.direction().name(),
                r.score(),
                r.tags().stream().map(Enum::name).toList(),
                r.comment(),
                r.createdAt().toString()
        );
    }

    // ── DTO ──────────────────────────────────────────────

    public record SubmitRatingRequest(
            @NotBlank String transactionId,
            @NotBlank String raterId,
            @NotBlank String rateeId,
            @NotBlank String direction,   // WORKER_TO_CONSUMER or CONSUMER_TO_WORKER
            @Min(1) @Max(5) int score,
            List<String> tags,
            String comment
    ) {
    }

    public record RatingResponse(
            String id,
            String transactionId,
            String raterId,
            String rateeId,
            String direction,
            int score,
            List<String> tags,
            String comment,
            String createdAt
    ) {
    }

    public record CreditProfileResponse(
            String memberId,
            double averageScore,
            int totalRatings,
            Map<String, Integer> tagFrequency,
            List<Integer> recentScores
    ) {
    }
}
