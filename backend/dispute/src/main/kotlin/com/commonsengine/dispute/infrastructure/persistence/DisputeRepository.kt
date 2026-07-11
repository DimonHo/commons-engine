package com.commonsengine.dispute.infrastructure.persistence

import com.commonsengine.dispute.domain.DisputeStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DisputeRepository : JpaRepository<DisputeEntity, Long> {

    fun findByDisputeId(disputeId: String): DisputeEntity?

    fun findByStatus(status: DisputeStatus): List<DisputeEntity>

    fun findByFiledBy(filedBy: String): List<DisputeEntity>

    fun findByFiledAgainst(filedAgainst: String): List<DisputeEntity>
}
