package com.commonsengine.dispatch.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 调度领域模型（#66）
 *
 * 公地引擎调度域——派单任务、劳动者偏好、路径建议。
 * Kotlin data class → Java record；带校验的类用 compact record + 构造器校验。
 */
public final class Model {

    private Model() {
    }

    /**
     * 劳动者偏好——服务类型、工作时段、地理位置。
     */
    public record WorkerPreferences(
            String workerId,
            Set<String> serviceTypes,
            TimeSlot workingHours,
            double maxServiceRadiusMeters
    ) {
        public WorkerPreferences {
            if (workerId == null || workerId.isBlank()) {
                throw new IllegalArgumentException("workerId 不能为空");
            }
            if (maxServiceRadiusMeters < 0) {
                throw new IllegalArgumentException("maxServiceRadiusMeters 不能为负: " + maxServiceRadiusMeters);
            }
            serviceTypes = serviceTypes == null ? Set.of() : Set.copyOf(serviceTypes);
        }
    }

    /**
     * 时间窗口——带校验（start <= end）。
     */
    public record TimeSlot(Instant start, Instant end) {
        public TimeSlot {
            if (start == null || end == null) {
                throw new IllegalArgumentException("TimeSlot 的 start/end 不能为空");
            }
            if (end.isBefore(start)) {
                throw new IllegalArgumentException(
                        "TimeSlot.end 必须不早于 start: start=" + start + ", end=" + end
                );
            }
        }

        /** 判断某时刻是否落在窗口内（闭区间）。 */
        public boolean contains(Instant t) {
            return !t.isBefore(start) && !t.isAfter(end);
        }
    }

    /**
     * 派单任务——一次服务派发。
     */
    public record DispatchTask(
            String id,
            String serviceType,
            double pickupLat,
            double pickupLng,
            String assignedWorkerId,
            TaskStatus status,
            Instant createdAt
    ) {
        public DispatchTask {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("DispatchTask.id 不能为空");
            }
            if (!Double.isFinite(pickupLat) || pickupLat < -90 || pickupLat > 90) {
                throw new IllegalArgumentException("非法纬度: " + pickupLat);
            }
            if (!Double.isFinite(pickupLng) || pickupLng < -180 || pickupLng > 180) {
                throw new IllegalArgumentException("非法经度: " + pickupLng);
            }
        }

        public static DispatchTask create(String serviceType, double pickupLat, double pickupLng) {
            return new DispatchTask(
                    UUID.randomUUID().toString(),
                    serviceType,
                    pickupLat,
                    pickupLng,
                    null,
                    TaskStatus.PENDING,
                    Instant.now()
            );
        }
    }

    /** 派单任务状态 */
    public enum TaskStatus {
        PENDING,       // 待分配
        ASSIGNED,      // 已分配
        ACCEPTED,      // 劳动者已接单
        DECLINED,      // 劳动者拒绝
        COMPLETED,     // 已完成
        CANCELLED      // 已取消
    }

    /**
     * 路径建议——优化后的劳动者访问顺序。
     */
    public record RouteSuggestion(
            String workerId,
            java.util.List<VisitPoint> visits,
            double totalDistanceMeters,
            long estimatedDurationMinutes,
            String strategy
    ) {
    }

    /** 路径上的一个访问点 */
    public record VisitPoint(
            int sequence,
            double lat,
            double lng,
            String taskId,
            String label
    ) {
    }
}
