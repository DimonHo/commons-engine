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
 * 纠纷仲裁 HTTP 层集成测试（#63 follow-up to PR #62）
 *
 * 验证 DisputeController 的 file → aiScreening → arbitrate → findById → findAll 链路。
 *
 * 关联：PR #62 维护者建议 #1——补充端点契约验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DisputeApiTest {

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
    fun `file dispute creates and returns dispute with FILED status`() {
        val body = """
            {"transactionId":"tx-d1","filedBy":"c-d1","filedAgainst":"w-d1","type":"FARE_DISPUTE","description":"多收了10元","evidenceUrls":["https://example.com/evidence1.png"]}
        """.trimIndent()
        val resp = post("/api/v1/dispute/file", body)
        assertEquals(200, resp.statusCode(), "file 应返回 200，实际: ${resp.statusCode()} body=${resp.body()}")

        val json = objectMapper.readTree(resp.body())
        assertEquals("FILED", json["status"].asText())
        assertEquals("FARE_DISPUTE", json["type"].asText())
        assertEquals("tx-d1", json["transactionId"].asText())
        assertTrue(json["id"].asText().isNotBlank())
    }

    @Test
    fun `ai screening classifies and returns priority`() {
        // file a dispute first
        val fileResp = post("/api/v1/dispute/file",
            """{"transactionId":"tx-d2","filedBy":"c-d2","filedAgainst":"w-d2","type":"BEHAVIORAL","description":"司机态度恶劣","evidenceUrls":[]}""")
        val disputeId = objectMapper.readTree(fileResp.body())["id"].asText()

        // ai screening
        val screenResp = post("/api/v1/dispute/$disputeId/screening", "")
        assertEquals(200, screenResp.statusCode(), "screening 应返回 200，实际: ${screenResp.statusCode()} body=${screenResp.body()}")

        val json = objectMapper.readTree(screenResp.body())
        assertEquals(disputeId, json["disputeId"].asText())
        // BEHAVIORAL → HIGH priority
        assertEquals("HIGH", json["suggestedPriority"].asText())
        assertTrue(json["confidence"].asDouble() > 0)
        assertTrue(json["reasoning"].asText().isNotBlank())
    }

    @Test
    fun `arbitrate resolves dispute with verdict`() {
        // file + screen first
        val fileResp = post("/api/v1/dispute/file",
            """{"transactionId":"tx-d3","filedBy":"c-d3","filedAgainst":"w-d3","type":"FARE_DISPUTE","description":"费用不符","evidenceUrls":[]}""")
        val disputeId = objectMapper.readTree(fileResp.body())["id"].asText()

        // ai screening to move to INVESTIGATION
        post("/api/v1/dispute/$disputeId/screening", "")

        // arbitrate
        val arbBody = """
            {"verdict":"FAVOR_FILER","reasoning":"证据显示多收费用","compensationAmount":"10.00"}
        """.trimIndent()
        val arbResp = post("/api/v1/dispute/$disputeId/arbitrate", arbBody)
        assertEquals(200, arbResp.statusCode(), "arbitrate 应返回 200，实际: ${arbResp.statusCode()} body=${arbResp.body()}")

        val json = objectMapper.readTree(arbResp.body())
        assertEquals("FAVOR_FILER", json["verdict"].asText())
        assertEquals("10.00", json["compensationAmount"].asText())
        assertTrue(json["decidedAt"].asText().isNotBlank())
    }

    @Test
    fun `find by id returns dispute details`() {
        val fileResp = post("/api/v1/dispute/file",
            """{"transactionId":"tx-d4","filedBy":"c-d4","filedAgainst":"w-d4","type":"SERVICE_QUALITY","description":"服务差","evidenceUrls":[]}""")
        val disputeId = objectMapper.readTree(fileResp.body())["id"].asText()

        val resp = get("/api/v1/dispute/$disputeId")
        assertEquals(200, resp.statusCode())
        val json = objectMapper.readTree(resp.body())
        assertEquals(disputeId, json["id"].asText())
        assertEquals("SERVICE_QUALITY", json["type"].asText())
    }

    @Test
    fun `find all returns list of disputes`() {
        // ensure at least one dispute exists
        post("/api/v1/dispute/file",
            """{"transactionId":"tx-d5","filedBy":"c-d5","filedAgainst":"w-d5","type":"OTHER","description":"测试工单","evidenceUrls":[]}""")

        val resp = get("/api/v1/dispute")
        assertEquals(200, resp.statusCode())
        val disputes = objectMapper.readTree(resp.body())
        assertTrue(disputes.size() >= 1, "应至少有 1 个工单")
    }

    @Test
    fun `find all with status filter works`() {
        // file + screen → ends up in INVESTIGATION status
        val fileResp = post("/api/v1/dispute/file",
            """{"transactionId":"tx-d6","filedBy":"c-d6","filedAgainst":"w-d6","type":"FARE_DISPUTE","description":"费用争议","evidenceUrls":[]}""")
        val disputeId = objectMapper.readTree(fileResp.body())["id"].asText()
        post("/api/v1/dispute/$disputeId/screening", "")

        // query by status=INVESTIGATION
        val resp = get("/api/v1/dispute?status=INVESTIGATION")
        assertEquals(200, resp.statusCode())
        val disputes = objectMapper.readTree(resp.body())
        assertTrue(disputes.size() >= 1)
        assertTrue(disputes.all { it["status"].asText() == "INVESTIGATION" })
    }
}
