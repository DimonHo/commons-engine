package com.commonsengine.governance.api;

import com.commonsengine.governance.domain.Model.Proposal;
import com.commonsengine.governance.domain.Model.ProposalId;
import com.commonsengine.governance.domain.Model.ProposalStatus;
import com.commonsengine.governance.domain.Model.ProposalType;
import com.commonsengine.governance.domain.Model.StakeholderType;
import com.commonsengine.governance.domain.Model.Vote;
import com.commonsengine.governance.domain.Model.VoteChoice;
import com.commonsengine.governance.domain.Model.VoteResult;
import com.commonsengine.governance.service.GovernanceService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 治理 REST API（#69 治理模块）
 *
 * 端点：
 * - POST   /api/v1/governance/proposals                创建提案
 * - GET    /api/v1/governance/proposals                 按状态查询
 * - GET    /api/v1/governance/proposals/{id}            查询提案详情
 * - POST   /api/v1/governance/proposals/{id}/open       开启投票
 * - POST   /api/v1/governance/proposals/{id}/vote       投票
 * - POST   /api/v1/governance/proposals/{id}/tally      计票决议
 * - GET    /api/v1/governance/proposals/{id}/votes      查询投票记录
 */
@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceController {

    private final GovernanceService service;

    public GovernanceController(GovernanceService service) {
        this.service = service;
    }

    @PostMapping("/proposals")
    public ResponseEntity<ProposalResponse> create(
            @RequestBody CreateProposalRequest request) {
        Proposal p = service.createProposal(
                request.title(), request.description(), request.type(), request.proposerId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ProposalResponse.from(p));
    }

    @GetMapping("/proposals")
    public List<ProposalResponse> list(
            @RequestParam(required = false) ProposalStatus status) {
        if (status == null) {
            return List.of();
        }
        return service.findByStatus(status).stream().map(ProposalResponse::from).toList();
    }

    @GetMapping("/proposals/{id}")
    public ProposalResponse getById(@PathVariable String id) {
        return ProposalResponse.from(service.findById(new ProposalId(id)));
    }

    @PostMapping("/proposals/{id}/open")
    public ProposalResponse openVoting(@PathVariable String id) {
        return ProposalResponse.from(service.openVoting(new ProposalId(id)));
    }

    @PostMapping("/proposals/{id}/vote")
    public VoteResponse castVote(@PathVariable String id,
                                  @RequestBody CastVoteRequest request) {
        Vote v = service.castVote(
                new ProposalId(id),
                request.voterId(),
                request.stakeholderType(),
                request.choice(),
                request.reason()
        );
        return VoteResponse.from(v);
    }

    @PostMapping("/proposals/{id}/tally")
    public VoteResultResponse tally(@PathVariable String id) {
        VoteResult result = service.tallyAndResolve(new ProposalId(id));
        return VoteResultResponse.from(result);
    }

    @GetMapping("/proposals/{id}/votes")
    public List<VoteResponse> votes(@PathVariable String id) {
        return service.findVotes(new ProposalId(id)).stream()
                .map(VoteResponse::from)
                .toList();
    }

    // ── DTO records ────────────────────────────────────────

    public record CreateProposalRequest(
            @NotBlank String title,
            String description,
            @NotNull ProposalType type,
            @NotBlank String proposerId
    ) {
    }

    public record CastVoteRequest(
            @NotBlank String voterId,
            @NotNull StakeholderType stakeholderType,
            @NotNull VoteChoice choice,
            String reason
    ) {
    }

    public record ProposalResponse(
            String id,
            String title,
            String description,
            ProposalType type,
            String proposerId,
            ProposalStatus status,
            Instant createdAt,
            Instant votingOpensAt,
            Instant votingClosesAt,
            long discussionDurationHours,
            long votingDurationHours
    ) {
        static ProposalResponse from(Proposal p) {
            return new ProposalResponse(
                    p.id().value(), p.title(), p.description(), p.type(), p.proposerId(),
                    p.status(), p.createdAt(), p.votingOpensAt(), p.votingClosesAt(),
                    p.discussionDurationHours(), p.votingDurationHours()
            );
        }
    }

    public record VoteResponse(
            String id,
            String proposalId,
            String voterId,
            StakeholderType stakeholderType,
            VoteChoice choice,
            String reason,
            Instant castAt
    ) {
        static VoteResponse from(Vote v) {
            return new VoteResponse(
                    v.id(), v.proposalId().value(), v.voterId(), v.stakeholderType(),
                    v.choice(), v.reason(), v.castAt()
            );
        }
    }

    public record VoteResultResponse(
            String proposalId,
            int totalVotes,
            int approveCount,
            int rejectCount,
            int abstainCount,
            double weightedScore,
            boolean passed
    ) {
        static VoteResultResponse from(VoteResult r) {
            return new VoteResultResponse(
                    r.proposalId().value(), r.totalVotes(), r.approveCount(),
                    r.rejectCount(), r.abstainCount(), r.weightedScore(), r.passed()
            );
        }
    }
}
