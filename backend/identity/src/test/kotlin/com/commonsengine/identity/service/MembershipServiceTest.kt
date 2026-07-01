package com.commonsengine.identity.service

import com.commonsengine.identity.domain.MemberId
import com.commonsengine.identity.domain.MemberRole
import com.commonsengine.identity.domain.MemberStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MembershipServiceTest {

    private val service = MembershipService()

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
        assertEquals(member.name, found.name)
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
        assertEquals(MemberStatus.SUSPENDED, suspended.status)
    }

    @Test
    fun `roleStatistics counts active members per role`() {
        service.register("worker1", "1", setOf(MemberRole.WORKER))
        service.register("worker2", "2", setOf(MemberRole.WORKER))
        service.register("consumer1", "3", setOf(MemberRole.CONSUMER))
        service.register("both", "4", setOf(MemberRole.WORKER, MemberRole.CONSUMER))

        val stats = service.roleStatistics()
        assertEquals(3, stats[MemberRole.WORKER])  // worker1, worker2, both
        assertEquals(2, stats[MemberRole.CONSUMER]) // consumer1, both
    }

    @Test
    fun `register rejects blank name`() {
        var threw = false
        try {
            service.register("", "123", setOf(MemberRole.WORKER))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `register rejects empty roles`() {
        var threw = false
        try {
            service.register("test", "123", emptySet())
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
