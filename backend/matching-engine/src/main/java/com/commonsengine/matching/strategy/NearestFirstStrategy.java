package com.commonsengine.matching.strategy;

import com.commonsengine.platform.domain.Model.MatchResult;
import com.commonsengine.platform.domain.Model.ServiceRequest;
import com.commonsengine.platform.domain.Model.Worker;
import com.commonsengine.platform.geo.GeoPoint;
import com.commonsengine.platform.geo.GeoUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 最近优先策略——选择距离服务起点最近的劳动者。
 *
 * 简单高效，适合对响应时间敏感的场景（如网约车）。
 * 但不保证公平性——可能持续派单给位置有利的少数劳动者。
 */
public final class NearestFirstStrategy implements MatchingStrategy {

    private final AntiExploitationConfig antiExploitationConfig;

    public NearestFirstStrategy() {
        this(AntiExploitationConfig.defaults());
    }

    public NearestFirstStrategy(AntiExploitationConfig antiExploitationConfig) {
        this.antiExploitationConfig = antiExploitationConfig;
    }

    @Override
    public MatchResult match(ServiceRequest request, List<Worker> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // 反榨取过滤：排除已达活跃订单上限的劳动者
        List<Worker> eligible = candidates.stream()
                .filter(w -> w.activeOrderCount() < antiExploitationConfig.maxActiveOrdersPerWorker())
                .toList();

        if (eligible.isEmpty()) {
            return null;
        }

        GeoPoint pickup = request.pickupLocation();

        Worker nearest = eligible.stream()
                .min(Comparator.comparingDouble(
                        w -> GeoUtils.distance(pickup, w.currentLocation())))
                .orElse(null);

        if (nearest == null) {
            return null;
        }

        double distance = GeoUtils.distance(pickup, nearest.currentLocation());

        // 越近分越高——score 用 (1 / (1 + distanceMeters)) 作为归一化相似度。
        double score = 1.0 / (1.0 + distance);

        return new MatchResult(nearest.id(), request.id(), score);
    }

    @Override
    public String name() {
        return "nearest-first";
    }
}
