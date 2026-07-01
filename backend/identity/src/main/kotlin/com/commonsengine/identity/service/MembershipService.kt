package com.commonsengine.identity.service

import com.commonsengine.identity.domain.Member
import com.commonsengine.identity.domain.MemberId
import com.commonsengine.identity.domain.MemberRole
import com.commonsengine.identity.domain.MemberStatus
import com.commonsengine.identity.domain.WorkerProfile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 会员管理服务
 *
 * 管理合作社成员的注册、查询、状态变更。
 * 当前使用内存存储（MVP），后续替换为 PostgreSQL。
 */
@Service
open class MembershipService {

    private val members = ConcurrentHashMap<String, Member>()
    private val workerProfiles = ConcurrentHashMap<String, WorkerProfile>()

    /** 注册新成员 */
    fun register(name: String, phone: String, roles: Set<MemberRole>): Member {
        require(name.isNotBlank()) { "成员姓名不能为空" }
        require(phone.isNotBlank()) { "手机号不能为空" }
        require(roles.isNotEmpty()) { "至少需要一个角色" }

        val member = Member(
            id = MemberId.random(),
            name = name,
            phone = phone,
            roles = roles,
        )
        members[member.id.value] = member
        return member
    }

    /** 查询成员 */
    fun findById(id: MemberId): Member? = members[id.value]

    /** 查询所有成员 */
    fun findAll(): List<Member> = members.values.toList()

    /** 注册劳动者档案 */
    fun registerWorkerProfile(profile: WorkerProfile): WorkerProfile {
        require(members.containsKey(profile.memberId.value)) {
            "成员 ${profile.memberId.value} 不存在，无法创建劳动者档案"
        }
        workerProfiles[profile.memberId.value] = profile
        return profile
    }

    /** 查询劳动者的档案 */
    fun findWorkerProfile(memberId: MemberId): WorkerProfile? = workerProfiles[memberId.value]

    /** 暂停成员（违反规则/调查中） */
    fun suspend(memberId: MemberId, reason: String): Member? {
        val member = members[memberId.value] ?: return null
        val updated = member.copy(status = MemberStatus.SUSPENDED)
        members[memberId.value] = updated
        return updated
    }

    /** 成员退社 */
    fun withdraw(memberId: MemberId): Member? {
        val member = members[memberId.value] ?: return null
        val updated = member.copy(status = MemberStatus.WITHDRAWN)
        members[memberId.value] = updated
        return updated
    }

    /** 统计：各角色人数 */
    fun roleStatistics(): Map<MemberRole, Int> {
        val activeMembers = members.values.filter { it.status == MemberStatus.ACTIVE }
        return MemberRole.entries.associateWith { role ->
            activeMembers.count { role in it.roles }
        }
    }
}
