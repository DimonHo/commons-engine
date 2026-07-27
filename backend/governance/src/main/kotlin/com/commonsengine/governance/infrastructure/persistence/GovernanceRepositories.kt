package com.commonsengine.governance.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProposalRepository : JpaRepository<ProposalEntity, Long> {

    /** 按提案 ID 查询 */
    fun findByProposalId(proposalId: String): ProposalEntity?

    /** 按状态查询提案 */
    fun findByStatus(status: com.commonsengine.governance.domain.ProposalStatus): List<ProposalEntity>
}

@Repository
interface VoteRepository : JpaRepository<VoteEntity, Long> {

    /** 按提案 ID 查询所有投票 */
    fun findByProposalId(proposalId: String): List<VoteEntity>

    /** 检查是否已投票（一人一票） */
    fun existsByProposalIdAndVoterId(proposalId: String, voterId: String): Boolean
}
