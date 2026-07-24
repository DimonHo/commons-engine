package com.commonsengine.dispatch.infrastructure.persistence;

import com.commonsengine.dispatch.domain.Model.TaskStatus;
import com.commonsengine.dispatch.infrastructure.persistence.DispatchPersistence.DispatchTaskEntity;
import com.commonsengine.dispatch.infrastructure.persistence.DispatchPersistence.WorkerPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 调度域 JPA Repository 接口集合（匹配 Kotlin DispatchRepositories.kt）。
 */
public final class DispatchRepositories {

    private DispatchRepositories() {
    }

    public interface DispatchTaskRepository extends JpaRepository<DispatchTaskEntity, String> {
        List<DispatchTaskEntity> findByStatusOrderByCreatedAtAsc(TaskStatus status);

        List<DispatchTaskEntity> findByAssignedWorkerIdOrderByCreatedAtDesc(String workerId);
    }

    public interface WorkerPreferencesRepository extends JpaRepository<WorkerPreferencesEntity, String> {
    }
}
