package com.commonsengine.identity.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface MemberRepository : JpaRepository<MemberEntity, String> {

    fun findByPhone(phone: String): Optional<MemberEntity>

    fun findByStatus(status: String): List<MemberEntity>
}

@Repository
interface WorkerProfileRepository : JpaRepository<WorkerProfileEntity, String>
