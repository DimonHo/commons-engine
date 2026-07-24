package com.commonsengine.governance.infrastructure.persistence;

import com.commonsengine.governance.domain.Model.ProposalStatus;
import com.commonsengine.governance.infrastructure.persistence.GovernancePersistence.ProposalEntity;
import com.commonsengine.governance.infrastructure.persistence.GovernancePersistence.VoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 治理域 JPA Repository 接口集合（匹配 Kotlin GovernanceRepositories.kt）。
 */
public final class GovernanceRepositories {

    private GovernanceRepositories() {
    }

    public interface ProposalRepository extends JpaRepository<ProposalEntity, String> {
        List<ProposalEntity> findByStatusOrderByCreatedAtDesc(ProposalStatus status);

        List<ProposalEntity> findByProposerIdOrderByCreatedAtDesc(String proposerId);
    }

    public interface VoteRepository extends JpaRepository<VoteEntity, String> {
        List<VoteEntity> findByProposalId(String proposalId);

        List<VoteEntity> findByProposalIdAndVoterId(String proposalId, String voterId);
    }
}
