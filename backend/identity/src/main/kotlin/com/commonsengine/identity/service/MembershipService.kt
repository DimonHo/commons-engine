package com.commonsengine.identity.service

import com.commonsengine.identity.domain.Member
import com.commonsengine.identity.domain.MemberId
import com.commonsengine.identity.domain.MemberRole
import com.commonsengine.identity.domain.MemberStatus
import com.commonsengine.identity.domain.WorkerProfile
import com.commonsengine.identity.infrastructure.persistence.MemberRepository
import com.commonsengine.identity.infrastructure.persistence.WorkerProfileRepository
import com.commonsengine.identity.infrastructure.persistence.toDomain
import com.commonsengine.identity.infrastructure.persistence.toEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 会员管理服务
 *
 * 管理合作社成员的注册、查询、状态变更。
 * 使用 PostgreSQL 持久化（通过 Spring Data JPA）。
 */
@Service
open class MembershipService(
    private val memberRepository: MemberRepository,
    private val workerProfileRepository: WorkerProfileRepository,
) {

    /** 注册新成员 */
    @Transactional
    open fun register(name: String, phone: String, roles: Set<MemberRole>): Member {
        require(name.isNotBlank()) { "成员姓名不能为空" }
        require(phone.isNotBlank()) { "手机号不能为空" }
        require(roles.isNotEmpty()) { "至少需要一个角色" }

        val member = Member(
            id = MemberId.random(),
            name = name,
            phone = phone,
            roles = roles,
        )
        val saved = memberRepository.save(member.toEntity())
        return saved.toDomain()
    }

    /** 查询成员 */
    @Transactional(readOnly = true)
    open fun findById(id: MemberId): Member? =
        memberRepository.findById(id.value).orElse(null)?.toDomain()

    /** 查询所有成员 */
    @Transactional(readOnly = true)
    open fun findAll(): List<Member> =
        memberRepository.findAll().map { it.toDomain() }

    /** 注册劳动者档案 */
    @Transactional
    open fun registerWorkerProfile(profile: WorkerProfile): WorkerProfile {
        require(memberRepository.existsById(profile.memberId.value)) {
            "成员 ${profile.memberId.value} 不存在，无法创建劳动者档案"
        }
        val saved = workerProfileRepository.save(profile.toEntity())
        return saved.toDomain()
    }

    /** 查询劳动者的档案 */
    @Transactional(readOnly = true)
    open fun findWorkerProfile(memberId: MemberId): WorkerProfile? =
        workerProfileRepository.findById(memberId.value).orElse(null)?.toDomain()

    /** 暂停成员（违反规则/调查中） */
    @Transactional
    open fun suspend(memberId: MemberId, reason: String): Member? {
        val entity = memberRepository.findById(memberId.value).orElse(null) ?: return null
        entity.status = MemberStatus.SUSPENDED.name
        return memberRepository.save(entity).toDomain()
    }

    /** 成员退社 */
    @Transactional
    open fun withdraw(memberId: MemberId): Member? {
        val entity = memberRepository.findById(memberId.value).orElse(null) ?: return null
        entity.status = MemberStatus.WITHDRAWN.name
        return memberRepository.save(entity).toDomain()
    }

    /** 统计：各角色人数 */
    @Transactional(readOnly = true)
    open fun roleStatistics(): Map<MemberRole, Int> {
        val activeMembers = memberRepository
            .findByStatus(MemberStatus.ACTIVE.name)
            .map { it.toDomain() }
        return MemberRole.entries.associateWith { role ->
            activeMembers.count { role in it.roles }
        }
    }
}
