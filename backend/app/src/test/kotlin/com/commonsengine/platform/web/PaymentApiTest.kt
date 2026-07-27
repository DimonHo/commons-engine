package com.commonsengine.platform.web

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper

/**
 * Payment HTTP integration test (P0 fix: payment integrity).
 *
 * Validates charge -> settle -> refund -> history over full HTTP path.
 *
 * Key contract change from original PR #62: settle and refund no longer
 * accept client-supplied transaction fields. The service reconstructs
 * the authoritative transaction from the CHARGE_CREATED event.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentApiTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val http: HttpClient = HttpClient.newHttpClient()

    private fun post(path: String, json: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun postEmpty(path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun get(path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port$path"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun charge(
        consumerId: String = "consumer-test",
        workerId: String = "worker-test",
        amount: String = "100.00",
        serviceType: String = "RIDE_HAILING",
    ): String {
        val body = """{"consumerId":"$consumerId","workerId":"$workerId","amount":"$amount","serviceType":"$serviceType"}"""
        val resp = post("/api/v1/payment/charge", body)
        assertEquals(200, resp.statusCode(), "charge should return 200: ${resp.statusCode()} body=${resp.body()}")
        return objectMapper.readTree(resp.body())["id"].asText()
    }

    @Test
    fun `charge creates transaction and returns CHARGED status`() {
        val txId = charge(amount = "35.50")
        assertTrue(txId.isNotBlank(), "should return transaction ID")
    }

    @Test
    fun `settle loads transaction from event store - no client-supplied fields`() {
        // 1. charge
        val txId = charge(consumerId = "c2", workerId = "w2", amount = "100.00")

        // 2. settle with NO body fields (just transactionId in path)
        val settleResp = postEmpty("/api/v1/payment/$txId/settle")
        assertEquals(200, settleResp.statusCode(), "settle should return 200: ${settleResp.statusCode()} body=${settleResp.body()}")

        val settleJson = objectMapper.readTree(settleResp.body())
        assertEquals("100.00", settleJson["totalAmount"].asText())
        // Default rule: 80/15/5
        assertEquals("80.00", settleJson["workerPayout"].asText())
        assertEquals("15.00", settleJson["platformFee"].asText())
        assertEquals("5.00", settleJson["commonsFund"].asText())
        assertTrue(settleJson["breakdown"].asText().contains("劳动者"), "breakdown 应包含劳动者分账信息: ${settleJson["breakdown"].asText()}")
    }

    @Test
    fun `settle returns 404 for non-existent transaction`() {
        val resp = postEmpty("/api/v1/payment/nonexistent-tx-id/settle")
        assertEquals(404, resp.statusCode(), "should return 404: ${resp.statusCode()}")
        val json = objectMapper.readTree(resp.body())
        assertEquals("NOT_FOUND", json["error"].asText())
    }

    @Test
    fun `refund loads transaction from event store - only needs reason`() {
        val txId = charge(consumerId = "c4", workerId = "w4", amount = "50.00")

        // refund only sends reason - no transaction fields
        val refundResp = post("/api/v1/payment/$txId/refund", """{"reason":"user cancelled"}""")
        assertEquals(200, refundResp.statusCode())
        val refundJson = objectMapper.readTree(refundResp.body())
        assertTrue(refundJson["success"].asBoolean())
        assertEquals(txId, refundJson["transactionId"].asText())
    }

    @Test
    fun `refund returns 404 for non-existent transaction`() {
        val resp = post("/api/v1/payment/nonexistent-tx-id/refund", """{"reason":"test"}""")
        assertEquals(404, resp.statusCode())
    }

    @Test
    fun `history returns all ledger events for a transaction`() {
        val txId = charge(consumerId = "c5", workerId = "w5", amount = "80.00")
        postEmpty("/api/v1/payment/$txId/settle")

        val historyResp = get("/api/v1/payment/$txId/history")
        assertEquals(200, historyResp.statusCode())
        val events = objectMapper.readTree(historyResp.body())
        assertTrue(events.size() >= 2, "should have at least charge + settle events")
        assertTrue(events.any { it["type"].asText() == "CHARGE_CREATED" })
        assertTrue(events.any { it["type"].asText() == "SETTLEMENT_COMPLETED" })
    }

    @Test
    fun `client cannot override settlement rate via API`() {
        // After P0 fix: SettleRequest DTO no longer exists.
        // Even if client sends workerRate in body, it's ignored.
        val txId = charge(consumerId = "c6", workerId = "w6", amount = "200.00")

        // Attempt to inject custom rates (should be ignored)
        val maliciousBody = """{"workerRate":0.50,"operationRate":0.40,"commonsRate":0.10}"""
        val settleResp = post("/api/v1/payment/$txId/settle", maliciousBody)
        assertEquals(200, settleResp.statusCode())

        val settleJson = objectMapper.readTree(settleResp.body())
        // Should use DEFAULT rule (80%), not the injected 50%
        assertEquals("160.00", settleJson["workerPayout"].asText())  // 200 * 0.80
        assertFalse(settleJson["workerPayout"].asText() == "100.00") // NOT 200 * 0.50
    }
}
