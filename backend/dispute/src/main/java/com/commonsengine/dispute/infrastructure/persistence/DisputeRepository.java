package com.commonsengine.dispute.infrastructure.persistence;

import com.commonsengine.dispute.domain.Model.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Dispute JPA Repository——按状态、当事人查询。
 */
public interface DisputeRepository extends JpaRepository<DisputeEntity, String> {

    List<DisputeEntity> findByStatusOrderByCreatedAtAsc(DisputeStatus status);

    @Query("SELECT d FROM DisputeEntity d WHERE d.consumerId = :consumerId OR d.workerId = :workerId "
            + "ORDER BY d.createdAt DESC")
    List<DisputeEntity> findByStakeholder(@Param("consumerId") String consumerId,
                                           @Param("workerId") String workerId);
}
