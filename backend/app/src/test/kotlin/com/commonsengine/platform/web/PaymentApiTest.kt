package com.commonsengine.platform.web

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper

/**
 * 支付分账 HTTP 层集成测试（#63 follow-up to PR #62）
 *
 * 验证 PaymentController 的 charge → settle → refund → history 链路
 * 走完整 HTTP 路径（真实 Tomcat + Jackson 反序列化），而非直接调用 service。
 *
 * 关联：PR #62 维护者建议 #1——补充 @WebMvcTest 或集成测试验证端点契约。
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

    private fun get(path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port$path"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `charge creates transaction and returns CHARGED status`() {
        val body = """
            {"consumerId":"consumer-1","workerId":"worker-1","amount":"35.50","serviceType":"RIDE_HAILING"}
        """.trimIndent()
        val resp = post("/api/v1/payment/charge", body)
        assertEquals(200, resp.statusCode(), "charge 应返回 200，实际: ${resp.statusCode()} body=${resp.body()}")

        val json = objectMapper.readTree(resp.body())
        assertEquals("CHARGED", json["status"].asText())
        assertTrue(json["id"].asText().isNotBlank(), "应返回交易 ID")
        assertEquals("35.50", json["amount"].asText())
    }

    @Test
    fun `settle with default rule distributes funds correctly`() {
        // 1. 先 charge
        val chargeBody = """
            {"consumerId":"consumer-2","workerId":"worker-2","amount":"100.00","serviceType":"RIDE_HAILING"}
        """.trimIndent()
        val chargeResp = post("/api/v1/payment/charge", chargeBody)
        assertEquals(200, chargeResp.statusCode())
        val chargeJson = objectMapper.readTree(chargeResp.body())
        val txId = chargeJson["id"].asText()

        // 2. settle（使用默认分账规则——不传 workerRate 等）
        val settleBody = """
            {"consumerId":"consumer-2","workerId":"worker-2","amount":"100.00","serviceType":"RIDE_HAILING"}
        """.trimIndent()
        val settleResp = post("/api/v1/payment/$txId/settle", settleBody)
        assertEquals(200, settleResp.statusCode(), "settle 应返回 200，实际: ${settleResp.statusCode()} body=${settleResp.body()}")

        val settleJson = objectMapper.readTree(settleResp.body())
        assertEquals("100.00", settleJson["totalAmount"].asText())
        // 默认规则 80/15/5
        assertEquals("80.00", settleJson["workerPayout"].asText())
        assertEquals("15.00", settleJson["platformFee"].asText())
        assertEquals("5.00", settleJson["commonsFund"].asText())
        assertTrue(settleJson["breakdown"].asText().contains("费用拆分"))
    }

    @Test
    fun `settle with custom rule respects anti-exploitation floor`() {
        // charge
        val chargeResp = post("/api/v1/payment/charge",
            """{"consumerId":"c3","workerId":"w3","amount":"200.00","serviceType":"FOOD_DELIVERY"}""")
        val txId = objectMapper.readTree(chargeResp.body())["id"].asText()

        // settle with custom rule: 70/20/10 (at the floor)
        val settleBody = """
            {"consumerId":"c3","workerId":"w3","amount":"200.00","serviceType":"FOOD_DELIVERY","workerRate":0.70,"operationRate":0.20,"commonsRate":0.10}
        """.trimIndent()
        val settleResp = post("/api/v1/payment/$txId/settle", settleBody)
        assertEquals(200, settleResp.statusCode())

        val settleJson = objectMapper.readTree(settleResp.body())
        assertEquals("140.00", settleJson["workerPayout"].asText())  // 200 * 0.70
        assertEquals("40.00", settleJson["platformFee"].asText())     // 200 * 0.20
        assertEquals("20.00", settleJson["commonsFund"].asText())     // 200 * 0.10
    }

    @Test
    fun `refund returns success for valid transaction`() {
        // charge
        val chargeResp = post("/api/v1/payment/charge",
            """{"consumerId":"c4","workerId":"w4","amount":"50.00","serviceType":"RIDE_HAILING"}""")
        val txId = objectMapper.readTree(chargeResp.body())["id"].asText()

        // refund
        val refundBody = """
            {"consumerId":"c4","workerId":"w4","amount":"50.00","serviceType":"RIDE_HAILING","reason":"用户取消订单"}
        """.trimIndent()
        val refundResp = post("/api/v1/payment/$txId/refund", refundBody)
        assertEquals(200, refundResp.statusCode())
        val refundJson = objectMapper.readTree(refundResp.body())
        assertTrue(refundJson["success"].asBoolean())
    }

    @Test
    fun `history returns all ledger events for a transaction`() {
        // charge + settle to create 2 events
        val chargeResp = post("/api/v1/payment/charge",
            """{"consumerId":"c5","workerId":"w5","amount":"80.00","serviceType":"RIDE_HAILING"}""")
        val txId = objectMapper.readTree(chargeResp.body())["id"].asText()

        post("/api/v1/payment/$txId/settle",
            """{"consumerId":"c5","workerId":"w5","amount":"80.00","serviceType":"RIDE_HAILING"}""")

        // query history
        val historyResp = get("/api/v1/payment/$txId/history")
        assertEquals(200, historyResp.statusCode())
        val events = objectMapper.readTree(historyResp.body())
        assertTrue(events.size() >= 2, "应至少有 charge + settle 两个事件")
        val types = events.map { it["type"].asText() }.toSet()
        assertTrue(types.contains("CHARGE_CREATED"), "应包含 CHARGE_CREATED 事件")
        assertTrue(types.contains("SETTLEMENT_COMPLETED"), "应包含 SETTLEMENT_COMPLETED 事件")
    }
}
