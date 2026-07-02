package com.commonsengine.identity.service

import com.commonsengine.identity.domain.MemberId
import com.commonsengine.identity.domain.MemberRole
import com.commonsengine.identity.domain.MemberStatus
import com.commonsengine.platform.domain.ServiceType
import com.commonsengine.identity.domain.WorkerProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MembershipServiceTest {

    @Autowired
    private lateinit var service: MembershipService

    @Test
    fun `register creates a new member`() {
        val member = service.register("张三", "13800000001", setOf(MemberRole.WORKER))

        assertEquals("张三", member.name)
        assertEquals(MemberStatus.ACTIVE, member.status)
        assertTrue(MemberRole.WORKER in member.roles)
    }

    @Test
    fun `findById returns registered member`() {
        val member = service.register("李四", "13800000002", setOf(MemberRole.CONSUMER))

        val found = service.findById(member.id)
        assertNotNull(found)
        assertEquals(member.name, found!!.name)
    }

    @Test
    fun `findById returns null for unknown id`() {
        val found = service.findById(MemberId("nonexistent"))
        assertNull(found)
    }

    @Test
    fun `suspend changes status to SUSPENDED`() {
        val member = service.register("王五", "13800000003", setOf(MemberRole.WORKER))

        val suspended = service.suspend(member.id, "违反行为准则")
        assertNotNull(suspended)
        assertEquals(MemberStatus.SUSPENDED, suspended!!.status)
    }

    @Test
    fun `withdraw changes status to WITHDRAWN`() {
        val member = service.register("赵六", "13800000004", setOf(MemberRole.WORKER))

        val withdrawn = service.withdraw(member.id)
        assertNotNull(withdrawn)
        assertEquals(MemberStatus.WITHDRAWN, withdrawn!!.status)
    }

    @Test
    fun `roleStatistics counts active members per role`() {
        service.register("worker1", "1", setOf(MemberRole.WORKER))
        service.register("worker2", "2", setOf(MemberRole.WORKER))
        service.register("consumer1", "3", setOf(MemberRole.CONSUMER))
        service.register("both", "4", setOf(MemberRole.WORKER, MemberRole.CONSUMER))

        val stats = service.roleStatistics()
        assertEquals(3, stats[MemberRole.WORKER])   // worker1, worker2, both
        assertEquals(2, stats[MemberRole.CONSUMER])  // consumer1, both
    }

    @Test
    fun `registerWorkerProfile succeeds for existing member`() {
        val member = service.register("骑手小王", "13900000001", setOf(MemberRole.WORKER))
        val profile = WorkerProfile(
            memberId = member.id,
            serviceTypes = setOf(ServiceType.FOOD_DELIVERY, ServiceType.ERRAND),
            workRegion = "海淀区",
        )

        val saved = service.registerWorkerProfile(profile)
        assertEquals(member.id, saved.memberId)
        assertTrue(ServiceType.FOOD_DELIVERY in saved.serviceTypes)

        // 验证可回查
        val found = service.findWorkerProfile(member.id)
        assertNotNull(found)
        assertEquals("海淀区", found!!.workRegion)
    }

    @Test
    fun `registerWorkerProfile fails for non-existent member`() {
        val profile = WorkerProfile(
            memberId = MemberId("ghost"),
            serviceTypes = setOf(ServiceType.RIDE_HAILING),
            workRegion = "朝阳区",
        )

        assertThrows<IllegalArgumentException> {
            service.registerWorkerProfile(profile)
        }
    }

    @Test
    fun `register rejects blank name`() {
        assertThrows<IllegalArgumentException> {
            service.register("", "123", setOf(MemberRole.WORKER))
        }
    }

    @Test
    fun `register rejects empty roles`() {
        assertThrows<IllegalArgumentException> {
            service.register("test", "123", emptySet())
        }
    }
}
