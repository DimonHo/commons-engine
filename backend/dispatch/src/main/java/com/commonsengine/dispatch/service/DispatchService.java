package com.commonsengine.dispatch.service;

import com.commonsengine.dispatch.domain.Model.DispatchTask;
import com.commonsengine.dispatch.domain.Model.RouteSuggestion;
import com.commonsengine.dispatch.domain.Model.TaskStatus;
import com.commonsengine.dispatch.domain.Model.VisitPoint;
import com.commonsengine.dispatch.domain.Model.WorkerPreferences;
import com.commonsengine.dispatch.infrastructure.persistence.DispatchPersistence.DispatchTaskEntity;
import com.commonsengine.dispatch.infrastructure.persistence.DispatchPersistence.WorkerPreferencesEntity;
import com.commonsengine.dispatch.infrastructure.persistence.DispatchRepositories.DispatchTaskRepository;
import com.commonsengine.dispatch.infrastructure.persistence.DispatchRepositories.WorkerPreferencesRepository;
import com.commonsengine.dispatch.infrastructure.persistence.DispatchPersistence.DispatchTaskMapper;
import com.commonsengine.dispatch.infrastructure.persistence.DispatchPersistence.WorkerPreferencesMapper;
import com.commonsengine.platform.exception.BusinessRuleException;
import com.commonsengine.platform.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度业务服务（#66）
 *
 * 职责：
 * 1. 创建/分配/查询派单任务
 * 2. 管理劳动者偏好
 * 3. 路径优化——贪心最近邻算法（nearest-neighbor greedy）
 *
 * 路径优化原则（对齐架构文档「反榨取 3.5」）：
 * - 不饥饿派单：无可用劳动者时返回空建议而非强制分配
 * - 可解释：建议附带 totalDistanceMeters 与 strategy 字段
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final DispatchTaskRepository taskRepository;
    private final WorkerPreferencesRepository preferencesRepository;

    public DispatchService(DispatchTaskRepository taskRepository,
                           WorkerPreferencesRepository preferencesRepository) {
        this.taskRepository = taskRepository;
        this.preferencesRepository = preferencesRepository;
    }

    @Transactional
    public DispatchTask createTask(String serviceType, double pickupLat, double pickupLng) {
        DispatchTask task = DispatchTask.create(serviceType, pickupLat, pickupLng);
        DispatchTaskEntity saved = taskRepository.save(DispatchTaskMapper.toEntity(task));
        log.info("派单任务已创建 id={} type={}", task.id(), serviceType);
        return DispatchTaskMapper.toDomain(saved);
    }

    @Transactional
    public DispatchTask assignTask(String taskId, String workerId) {
        DispatchTaskEntity entity = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("DispatchTask", taskId));
        if (entity.getStatus() != TaskStatus.PENDING) {
            throw new BusinessRuleException(
                    "DISPATCH_TASK_NOT_ASSIGNABLE",
                    "派单任务状态必须为 PENDING 才能分配，当前: " + entity.getStatus()
            );
        }
        entity.setAssignedWorkerId(workerId);
        entity.setStatus(TaskStatus.ASSIGNED);
        DispatchTaskEntity saved = taskRepository.save(entity);
        log.info("派单任务已分配 id={} worker={}", taskId, workerId);
        return DispatchTaskMapper.toDomain(saved);
    }

    @Transactional(readOnly = true)
    public List<DispatchTask> findPendingTasks() {
        return taskRepository.findByStatusOrderByCreatedAtAsc(TaskStatus.PENDING).stream()
                .map(DispatchTaskMapper::toDomain)
                .toList();
    }

    @Transactional
    public WorkerPreferences savePreferences(WorkerPreferences preferences) {
        WorkerPreferencesEntity saved = preferencesRepository.save(
                WorkerPreferencesMapper.toEntity(preferences)
        );
        return WorkerPreferencesMapper.toDomain(saved);
    }

    @Transactional(readOnly = true)
    public WorkerPreferences findPreferences(String workerId) {
        return preferencesRepository.findById(workerId)
                .map(WorkerPreferencesMapper::toDomain)
                .orElseThrow(() -> new NotFoundException("WorkerPreferences", workerId));
    }

    /**
     * 贪心最近邻路径优化。
     *
     * 算法：从起点出发，每一步选择距离当前点最近的未访问点，
     * 直至所有点访问完毕。时间复杂度 O(n²)，适合小规模（n < 50）调度场景。
     *
     * @param originLat  起点纬度
     * @param originLng  起点经度
     * @param points     待访问点列表（lat, lng, label）
     * @param workerId   目标劳动者 ID
     * @return 优化后的路径建议；points 为空时返回空路径
     */
    public RouteSuggestion optimizeRoute(double originLat, double originLng,
                                          List<double[]> points, String workerId) {
        if (points == null || points.isEmpty()) {
            return new RouteSuggestion(workerId, List.of(), 0, 0, "nearest_neighbor_greedy");
        }

        List<VisitPoint> unvisited = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            double[] p = points.get(i);
            unvisited.add(new VisitPoint(0, p[0], p[1], null, "point-" + i));
        }

        List<VisitPoint> route = new ArrayList<>(unvisited.size());
        double currentLat = originLat;
        double currentLng = originLng;
        double totalDistance = 0;
        int sequence = 1;

        while (!unvisited.isEmpty()) {
            int nearestIdx = 0;
            double nearestDist = Double.MAX_VALUE;
            for (int i = 0; i < unvisited.size(); i++) {
                VisitPoint vp = unvisited.get(i);
                double dist = haversine(currentLat, currentLng, vp.lat(), vp.lng());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestIdx = i;
                }
            }
            VisitPoint chosen = unvisited.remove(nearestIdx);
            totalDistance += nearestDist;
            currentLat = chosen.lat();
            currentLng = chosen.lng();
            route.add(new VisitPoint(sequence++, chosen.lat(), chosen.lng(),
                    chosen.taskId(), chosen.label()));
        }

        // 粗略估计时长：均速 25km/h（城市道路）
        long estimatedMinutes = (long) Math.ceil((totalDistance / 1000.0) / 25.0 * 60);

        log.info("路径优化完成 worker={} visits={} distance={}m est={}min",
                workerId, route.size(), Math.round(totalDistance), estimatedMinutes);
        return new RouteSuggestion(workerId, route, totalDistance, estimatedMinutes,
                "nearest_neighbor_greedy");
    }

    /** Haversine 球面距离（米） */
    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return R * c;
    }
}
