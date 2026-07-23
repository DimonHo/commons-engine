package com.commonsengine.matching.strategy;

import com.commonsengine.platform.domain.Model.MatchResult;
import com.commonsengine.platform.domain.Model.ServiceRequest;
import com.commonsengine.platform.domain.Model.Worker;
import com.commonsengine.platform.geo.GeoPoint;
import com.commonsengine.platform.geo.GeoUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公平轮转策略——在距离阈值内按轮转计数器轮换分配，保证公平性。
 *
 * 维护每名劳动者的 rotationCounter：每次派单后计数 +1，
 * 在距离阈值内的候选中优先选择计数最低的劳动者。
 *
 * 体现「反榨取」与「公平分配」原则——避免少数劳动者垄断订单。
 */
public final class FairRoundRobinStrategy implements MatchingStrategy {

    /** 距离阈值（米）——超出此距离的候选不参与轮转 */
    private static final double DISTANCE_THRESHOLD_METERS = 5_000;

    private final AntiExploitationConfig antiExploitationConfig;
    /** 轮转计数器：workerId → 已派单次数 */
    private final Map<String, Integer> rotationCounter = new ConcurrentHashMap<>();

    public FairRoundRobinStrategy() {
        this(AntiExploitationConfig.defaults());
    }

    public FairRoundRobinStrategy(AntiExploitationConfig antiExploitationConfig) {
        this.antiExploitationConfig = antiExploitationConfig;
    }

    @Override
    public MatchResult match(ServiceRequest request, List<Worker> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        GeoPoint pickup = request.pickupLocation();

        // 过滤：反榨取 + 距离阈值
        List<Worker> eligible = candidates.stream()
                .filter(w -> w.activeOrderCount() < antiExploitationConfig.maxActiveOrdersPerWorker())
                .filter(w -> GeoUtils.distance(pickup, w.currentLocation())
                        <= DISTANCE_THRESHOLD_METERS)
                .toList();

        if (eligible.isEmpty()) {
            return null;
        }

        // 公平轮转：选择 rotationCounter 最低的；并列时选距离最近的
        Worker chosen = eligible.stream()
                .min(Comparator
                        .comparingInt((Worker w) -> rotationCounter.getOrDefault(workerKey(w), 0))
                        .thenComparingDouble(w -> GeoUtils.distance(pickup, w.currentLocation())))
                .orElse(null);

        if (chosen == null) {
            return null;
        }

        // 更新轮转计数
        rotationCounter.merge(workerKey(chosen), 1, Integer::sum);

        double distance = GeoUtils.distance(pickup, chosen.currentLocation());

        // 越近分越高——score 用 (1 / (1 + distanceMeters)) 作为归一化相似度。
        double score = 1.0 / (1.0 + distance);

        return new MatchResult(chosen.id(), request.id(), score);
    }

    /**
     * 提取 worker 标识作为轮转计数器 key。
     *
     * WorkerId 是 record（WorkerId(String value)），其 toString() 返回 value。
     */
    private static String workerKey(Worker w) {
        return w.id().toString();
    }

    @Override
    public String name() {
        return "fair-round-robin";
    }

    /** 测试可见——重置轮转计数器 */
    void resetRotation() {
        rotationCounter.clear();
    }
}
