package com.commonsengine.governance.infrastructure.persistence;

import com.commonsengine.governance.domain.Model.Proposal;
import com.commonsengine.governance.domain.Model.ProposalId;
import com.commonsengine.governance.domain.Model.ProposalStatus;
import com.commonsengine.governance.domain.Model.ProposalType;
import com.commonsengine.governance.domain.Model.StakeholderType;
import com.commonsengine.governance.domain.Model.Vote;
import com.commonsengine.governance.domain.Model.VoteChoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 治理域持久化聚合——ProposalEntity + VoteEntity + Mappers（匹配 Kotlin GovernancePersistence.kt）。
 */
public final class GovernancePersistence {

    private GovernancePersistence() {
    }

    @Entity
    @Table(name = "proposals")
    public static class ProposalEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "id", columnDefinition = "uuid")
        private String id;

        @Column(name = "title", nullable = false)
        private String title;

        @Column(name = "description", columnDefinition = "text")
        private String description;

        @Enumerated(EnumType.STRING)
        @Column(name = "type", nullable = false)
        private ProposalType type;

        @Column(name = "proposer_id", nullable = false)
        private String proposerId;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false)
        private ProposalStatus status;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        @Column(name = "voting_opens_at")
        private Instant votingOpensAt;

        @Column(name = "voting_closes_at")
        private Instant votingClosesAt;

        @Column(name = "discussion_duration_hours")
        private long discussionDurationHours;

        @Column(name = "voting_duration_hours")
        private long votingDurationHours;

        public ProposalEntity() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public ProposalType getType() {
            return type;
        }

        public void setType(ProposalType type) {
            this.type = type;
        }

        public String getProposerId() {
            return proposerId;
        }

        public void setProposerId(String proposerId) {
            this.proposerId = proposerId;
        }

        public ProposalStatus getStatus() {
            return status;
        }

        public void setStatus(ProposalStatus status) {
            this.status = status;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getVotingOpensAt() {
            return votingOpensAt;
        }

        public void setVotingOpensAt(Instant votingOpensAt) {
            this.votingOpensAt = votingOpensAt;
        }

        public Instant getVotingClosesAt() {
            return votingClosesAt;
        }

        public void setVotingClosesAt(Instant votingClosesAt) {
            this.votingClosesAt = votingClosesAt;
        }

        public long getDiscussionDurationHours() {
            return discussionDurationHours;
        }

        public void setDiscussionDurationHours(long discussionDurationHours) {
            this.discussionDurationHours = discussionDurationHours;
        }

        public long getVotingDurationHours() {
            return votingDurationHours;
        }

        public void setVotingDurationHours(long votingDurationHours) {
            this.votingDurationHours = votingDurationHours;
        }
    }

    @Entity
    @Table(name = "votes")
    public static class VoteEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(name = "id", columnDefinition = "uuid")
        private String id;

        @Column(name = "proposal_id", nullable = false)
        private String proposalId;

        @Column(name = "voter_id", nullable = false)
        private String voterId;

        @Enumerated(EnumType.STRING)
        @Column(name = "stakeholder_type", nullable = false)
        private StakeholderType stakeholderType;

        @Enumerated(EnumType.STRING)
        @Column(name = "choice", nullable = false)
        private VoteChoice choice;

        @Column(name = "reason", columnDefinition = "text")
        private String reason;

        @Column(name = "cast_at", nullable = false)
        private Instant castAt;

        public VoteEntity() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getProposalId() {
            return proposalId;
        }

        public void setProposalId(String proposalId) {
            this.proposalId = proposalId;
        }

        public String getVoterId() {
            return voterId;
        }

        public void setVoterId(String voterId) {
            this.voterId = voterId;
        }

        public StakeholderType getStakeholderType() {
            return stakeholderType;
        }

        public void setStakeholderType(StakeholderType stakeholderType) {
            this.stakeholderType = stakeholderType;
        }

        public VoteChoice getChoice() {
            return choice;
        }

        public void setChoice(VoteChoice choice) {
            this.choice = choice;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Instant getCastAt() {
            return castAt;
        }

        public void setCastAt(Instant castAt) {
            this.castAt = castAt;
        }
    }

    /** Proposal 静态映射器 */
    public static final class ProposalMapper {
        private ProposalMapper() {
        }

        public static Proposal toDomain(ProposalEntity e) {
            if (e == null) {
                return null;
            }
            return new Proposal(
                    new ProposalId(e.getId()),
                    e.getTitle(),
                    e.getDescription(),
                    e.getType(),
                    e.getProposerId(),
                    e.getStatus(),
                    e.getCreatedAt(),
                    e.getVotingOpensAt(),
                    e.getVotingClosesAt(),
                    e.getDiscussionDurationHours(),
                    e.getVotingDurationHours()
            );
        }

        public static ProposalEntity toEntity(Proposal p) {
            ProposalEntity e = new ProposalEntity();
            e.setId(p.id().value());
            e.setTitle(p.title());
            e.setDescription(p.description());
            e.setType(p.type());
            e.setProposerId(p.proposerId());
            e.setStatus(p.status());
            e.setCreatedAt(p.createdAt());
            e.setVotingOpensAt(p.votingOpensAt());
            e.setVotingClosesAt(p.votingClosesAt());
            e.setDiscussionDurationHours(p.discussionDurationHours());
            e.setVotingDurationHours(p.votingDurationHours());
            return e;
        }
    }

    /** Vote 静态映射器 */
    public static final class VoteMapper {
        private VoteMapper() {
        }

        public static Vote toDomain(VoteEntity e) {
            if (e == null) {
                return null;
            }
            return new Vote(
                    e.getId(),
                    new ProposalId(e.getProposalId()),
                    e.getVoterId(),
                    e.getStakeholderType(),
                    e.getChoice(),
                    e.getReason(),
                    e.getCastAt()
            );
        }

        public static VoteEntity toEntity(Vote v) {
            VoteEntity e = new VoteEntity();
            e.setId(v.id());
            e.setProposalId(v.proposalId().value());
            e.setVoterId(v.voterId());
            e.setStakeholderType(v.stakeholderType());
            e.setChoice(v.choice());
            e.setReason(v.reason());
            e.setCastAt(v.castAt());
            return e;
        }
    }
}
