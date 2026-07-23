package com.commonsengine.identity.service;

import com.commonsengine.identity.domain.Model.Member;
import com.commonsengine.identity.domain.Model.MemberId;
import com.commonsengine.identity.domain.MemberRole;
import com.commonsengine.identity.domain.MemberStatus;
import com.commonsengine.identity.domain.Model.WorkerProfile;
import com.commonsengine.identity.infrastructure.persistence.MemberEntity;
import com.commonsengine.identity.infrastructure.persistence.MemberMapper;
import com.commonsengine.identity.infrastructure.persistence.MemberRepository;
import com.commonsengine.identity.infrastructure.persistence.WorkerProfileMapper;
import com.commonsengine.identity.infrastructure.persistence.WorkerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会员管理服务。
 *
 * <p>管理合作社成员的注册、查询、状态变更。
 * 使用 PostgreSQL 持久化（通过 Spring Data JPA）。
 */
@Service
public class MembershipService {

    private final MemberRepository memberRepository;
    private final WorkerProfileRepository workerProfileRepository;

    public MembershipService(MemberRepository memberRepository,
                             WorkerProfileRepository workerProfileRepository) {
        this.memberRepository = memberRepository;
        this.workerProfileRepository = workerProfileRepository;
    }

    /** 注册新成员 */
    @Transactional
    public Member register(String name, String phone, java.util.Set<MemberRole> roles) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("成员姓名不能为空");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个角色");
        }

        Member member = new Member(
                MemberId.random(),
                name,
                phone,
                roles,
                java.time.Instant.now(),
                MemberStatus.ACTIVE,
                0
        );
        MemberEntity saved = memberRepository.save(MemberMapper.toEntity(member));
        return MemberMapper.toDomain(saved);
    }

    /** 查询成员 */
    @Transactional(readOnly = true)
    public Optional<Member> findById(MemberId id) {
        return memberRepository.findById(id.value()).map(MemberMapper::toDomain);
    }

    /** 查询所有成员 */
    @Transactional(readOnly = true)
    public List<Member> findAll() {
        return memberRepository.findAll().stream()
                .map(MemberMapper::toDomain)
                .toList();
    }

    /** 注册劳动者档案 */
    @Transactional
    public WorkerProfile registerWorkerProfile(WorkerProfile profile) {
        if (!memberRepository.existsById(profile.memberId().value())) {
            throw new IllegalArgumentException(
                    "成员 " + profile.memberId().value() + " 不存在，无法创建劳动者档案");
        }
        var saved = workerProfileRepository.save(WorkerProfileMapper.toEntity(profile));
        return WorkerProfileMapper.toDomain(saved);
    }

    /** 查询劳动者的档案 */
    @Transactional(readOnly = true)
    public Optional<WorkerProfile> findWorkerProfile(MemberId memberId) {
        return workerProfileRepository.findById(memberId.value()).map(WorkerProfileMapper::toDomain);
    }

    /** 暂停成员（违反规则/调查中） */
    @Transactional
    public Optional<Member> suspend(MemberId memberId, String reason) {
        return memberRepository.findById(memberId.value()).map(entity -> {
            MemberMapper.assignStatus(entity, MemberStatus.SUSPENDED);
            return MemberMapper.toDomain(memberRepository.save(entity));
        });
    }

    /** 成员退社 */
    @Transactional
    public Optional<Member> withdraw(MemberId memberId) {
        return memberRepository.findById(memberId.value()).map(entity -> {
            MemberMapper.assignStatus(entity, MemberStatus.WITHDRAWN);
            return MemberMapper.toDomain(memberRepository.save(entity));
        });
    }

    /** 统计：各角色人数 */
    @Transactional(readOnly = true)
    public Map<MemberRole, Integer> roleStatistics() {
        List<Member> activeMembers = memberRepository
                .findByStatus(MemberStatus.ACTIVE.name()).stream()
                .map(MemberMapper::toDomain)
                .toList();
        Map<MemberRole, Integer> stats = new EnumMap<>(MemberRole.class);
        for (MemberRole role : MemberRole.values()) {
            int count = 0;
            for (Member m : activeMembers) {
                if (m.roles().contains(role)) {
                    count++;
                }
            }
            stats.put(role, count);
        }
        return stats;
    }
}
