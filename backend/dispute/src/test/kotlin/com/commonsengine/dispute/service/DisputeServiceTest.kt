package com.commonsengine.dispute.service

import com.commonsengine.dispute.domain.DisputePriority
import com.commonsengine.dispute.domain.DisputeStatus
import com.commonsengine.dispute.domain.DisputeType
import com.commonsengine.dispute.domain.VerdictType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DisputeServiceTest {

    @Autowired
    private lateinit var service: DisputeService

    @Test
    fun `file creates dispute in FILED status`() {
        val d = service.file("tx-1", "c1", "w1", DisputeType.FARE_DISPUTE, "多收了钱")
        assertEquals(DisputeStatus.FILED, d.status)
        assertNotNull(d.id)
    }

    @Test
    fun `ai screening assigns HIGH priority for behavioral issues`() {
        val d = service.file("tx-1", "c1", "w1", DisputeType.BEHAVIORAL, "司机辱骂乘客")
        val result = service.aiScreening(d.id)

        assertEquals(DisputePriority.HIGH, result.suggestedPriority)
        assertTrue(result.reasoning.contains("人工调查"))
    }

    @Test
    fun `ai screening can auto-resolve low priority with evidence`() {
        val d = service.file("tx-1", "c1", "w1", DisputeType.SERVICE_QUALITY,
            "司机迟到", evidenceUrls = listOf("screenshot.png"))
        val result = service.aiScreening(d.id)

        assertTrue(result.canAutoResolve)
        assertEquals(DisputeStatus.AUTO_RESOLVED, service.findById(d.id)!!.status)
    }

    @Test
    fun `ai screening sends to investigation when not auto-resolvable`() {
        val d = service.file("tx-1", "c1", "w1", DisputeType.DAMAGE_CLAIM, "损坏货物")
        service.aiScreening(d.id)

        assertEquals(DisputeStatus.INVESTIGATION, service.findById(d.id)!!.status)
    }

    @Test
    fun `arbitrate resolves dispute with verdict`() {
        val d = service.file("tx-1", "c1", "w1", DisputeType.FARE_DISPUTE, "多收费")
        service.aiScreening(d.id) // → INVESTIGATION

        val verdict = service.arbitrate(
            disputeId = d.id,
            verdict = VerdictType.FAVOR_FILER,
            reasoning = "经查证，确有多收 10 元的情况，支持投诉方。",
            compensationAmount = BigDecimal("10.00"),
        )

        assertEquals(VerdictType.FAVOR_FILER, verdict.verdict)
        assertEquals(DisputeStatus.RESOLVED, service.findById(d.id)!!.status)
        assertNotNull(verdict.compensationAmount)
    }

    @Test
    fun `cannot arbitrate already resolved dispute`() {
        val d = service.file("tx-1", "c1", "w1", DisputeType.FARE_DISPUTE, "多收费")
        service.aiScreening(d.id)
        service.arbitrate(d.id, VerdictType.FAVOR_FILER, "理由")

        assertThrows<IllegalArgumentException> {
            service.arbitrate(d.id, VerdictType.FAVOR_RESPONDENT, "再次裁决")
        }
    }

    @Test
    fun `findByStatus filters correctly`() {
        val d1 = service.file("tx-1", "c1", "w1", DisputeType.FARE_DISPUTE, "投诉1")
        val d2 = service.file("tx-2", "c2", "w2", DisputeType.BEHAVIORAL, "投诉2")
        service.aiScreening(d2.id) // → INVESTIGATION

        val filed = service.findByStatus(DisputeStatus.FILED)
        assertEquals(1, filed.size)
        assertEquals(d1.id, filed[0].id)
    }

    @Test
    fun `evidence urls persist and reload`() {
        val d = service.file("tx-1", "c1", "w1", DisputeType.DAMAGE_CLAIM, "损坏货物",
            evidenceUrls = listOf("img1.png", "img2.png"))

        val reloaded = service.findById(d.id)!!
        assertEquals(2, reloaded.evidenceUrls.size)
        assertTrue(reloaded.evidenceUrls.contains("img1.png"))
        assertTrue(reloaded.evidenceUrls.contains("img2.png"))
    }

    @Test
    fun `resolution text persists after arbitration`() {
        val d = service.file("tx-1", "c1", "w1", DisputeType.FARE_DISPUTE, "多收费")
        service.aiScreening(d.id)
        service.arbitrate(d.id, VerdictType.FAVOR_FILER, "确有多收")

        val reloaded = service.findById(d.id)!!
        assertNotNull(reloaded.resolution)
        assertTrue(reloaded.resolution!!.contains("FAVOR_FILER"))
        assertNotNull(reloaded.resolvedAt)
    }

    @Test
    fun `findAll returns all disputes`() {
        service.file("tx-1", "c1", "w1", DisputeType.FARE_DISPUTE, "投诉1")
        service.file("tx-2", "c2", "w2", DisputeType.BEHAVIORAL, "投诉2")

        val all = service.findAll()
        assertEquals(2, all.size)
    }
}
