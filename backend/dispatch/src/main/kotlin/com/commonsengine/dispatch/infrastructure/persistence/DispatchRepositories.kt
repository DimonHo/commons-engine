package com.commonsengine.dispatch.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DispatchTaskRepository : JpaRepository<DispatchTaskEntity, Long> {

    /** 查询任务 by task_id */
    fun findByTaskId(taskId: String): DispatchTaskEntity?

    /** 查询某劳动者的所有任务 */
    fun findByWorkerId(workerId: String): List<DispatchTaskEntity>
}

@Repository
interface WorkerPreferencesRepository : JpaRepository<WorkerPreferencesEntity, Long> {

    /** 查询某劳动者的偏好（唯一） */
    fun findByWorkerId(workerId: String): WorkerPreferencesEntity?

    /** 检查是否存在 */
    fun existsByWorkerId(workerId: String): Boolean
}
