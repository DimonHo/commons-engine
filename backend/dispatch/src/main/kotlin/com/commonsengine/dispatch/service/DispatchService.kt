package com.commonsengine.dispatch.service

import com.commonsengine.dispatch.domain.DispatchTask
import com.commonsengine.dispatch.domain.RouteSuggestion
import com.commonsengine.dispatch.domain.WorkerPreferences
import com.commonsengine.dispatch.infrastructure.persistence.DispatchTaskRepository
import com.commonsengine.dispatch.infrastructure.persistence.WorkerPreferencesRepository
import com.commonsengine.dispatch.infrastructure.persistence.toDomain
import com.commonsengine.dispatch.infrastructure.persistence.toEntity
import com.commonsengine.platform.geo.GeoPoint
import com.commonsengine.platform.geo.GeoUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 调度引擎——路径优化与任务安排
 *
 * 设计原则（架构文档 3.5 条）：
 * 1. 路径优化服务于劳动者效率，而非平台抽成最大化
 * 2. 劳动者可设定工作偏好和边界，引擎尊重这些设定
 * 3. 支持多模式调度（打车实时+预约、外卖多取多送、家政预约）
 *
 * 持久化：使用 JPA + PostgreSQL，重启不丢数据。
 */
@Service
open class DispatchService(
    private val dispatchTaskRepository: DispatchTaskRepository,
    private val workerPreferencesRepository: WorkerPreferencesRepository,
) {

    // ── 任务持久化 ──────────────────────────────────────

    /** 保存调度任务 */
    @Transactional
    open fun assignTask(task: DispatchTask): DispatchTask {
        dispatchTaskRepository.save(task.toEntity())
        return task
    }

    /** 查询任务 */
    @Transactional(readOnly = true)
    open fun findTask(taskId: String): DispatchTask? =
        dispatchTaskRepository.findByTaskId(taskId)?.toDomain()

    /** 查询某劳动者的所有任务 */
    @Transactional(readOnly = true)
    open fun findTasksByWorker(workerId: String): List<DispatchTask> =
        dispatchTaskRepository.findByWorkerId(workerId).map { it.toDomain() }

    // ── 工作偏好持久化 ──────────────────────────────────

    /** 保存或更新劳动者工作偏好 */
    @Transactional
    open fun savePreferences(prefs: WorkerPreferences): WorkerPreferences {
        val existing = workerPreferencesRepository.findByWorkerId(prefs.workerId)
        if (existing != null) {
            // 更新已有记录
            val updated = prefs.toEntity().copy(id = existing.id)
            workerPreferencesRepository.save(updated)
        } else {
            workerPreferencesRepository.save(prefs.toEntity())
        }
        return prefs
    }

    /** 查询劳动者工作偏好 */
    @Transactional(readOnly = true)
    open fun findPreferences(workerId: String): WorkerPreferences? =
        workerPreferencesRepository.findByWorkerId(workerId)?.toDomain()

    // ── 调度逻辑 ────────────────────────────────────────

    /**
     * 检查劳动者是否愿意接受该任务（基于偏好）
     */
    @Transactional(readOnly = true)
    open fun isAcceptableForWorker(task: DispatchTask, prefs: WorkerPreferences): Boolean {
        // 服务类型偏好
        if (prefs.preferredServiceTypes.isNotEmpty() && task.serviceType !in prefs.preferredServiceTypes) {
            return false
        }

        // 时间限制（简化版：如果当前时间在排除时段内则拒绝）
        // 完整实现需要时钟注入

        // 最大同时接单数
        // 完整实现需要查询当前活跃订单数

        return true
    }

    /**
     * 优化路径——最近邻贪心算法（MVP）
     *
     * 对多个取送点，计算一个较优的访问顺序。
     * 后续可替换为更复杂的 VRP 算法。
     */
    open fun optimizeRoute(
        workerLocation: GeoPoint,
        task: DispatchTask,
    ): RouteSuggestion {
        val allPoints = task.pickups + task.dropoffs
        if (allPoints.isEmpty()) {
            return RouteSuggestion(
                workerId = task.workerId,
                orderedWaypoints = emptyList(),
                totalDistanceMeters = 0.0,
                estimatedTotalMinutes = 0,
                reason = "无路径点",
            )
        }

        // 最近邻贪心：从当前位置出发，每次找最近的未访问点
        val unvisited = allPoints.toMutableList()
        val ordered = mutableListOf<GeoPoint>()
        var current = workerLocation
        var totalDistance = 0.0

        while (unvisited.isNotEmpty()) {
            val nearest = unvisited.minByOrNull { GeoUtils.distance(current, it) }!!
            totalDistance += GeoUtils.distance(current, nearest)
            ordered.add(nearest)
            current = nearest
            unvisited.remove(nearest)
        }

        // 预估时间：按平均速度 25km/h（城市道路）
        val estimatedMinutes = (totalDistance / 1000.0 / 25.0 * 60).toInt()

        return RouteSuggestion(
            workerId = task.workerId,
            orderedWaypoints = ordered,
            totalDistanceMeters = totalDistance,
            estimatedTotalMinutes = estimatedMinutes,
            reason = buildString {
                append("最近邻优化路径，共 ${ordered.size} 个途经点。")
                append("总距离约 ${"%.1f".format(totalDistance / 1000)}km，")
                append("预估 ${estimatedMinutes} 分钟。")
                append("此路线服务于劳动者效率，非平台抽成最大化。")
            },
        )
    }
}
