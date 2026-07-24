package com.commonsengine.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 劳动者档案 Repository。
 */
@Repository
public interface WorkerProfileRepository extends JpaRepository<WorkerProfileEntity, String> {
}
