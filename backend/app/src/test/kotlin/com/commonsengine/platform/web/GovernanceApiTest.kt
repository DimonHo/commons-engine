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
 * 治理模块 HTTP 层集成测试（#63 follow-up to PR #62）
 *
 * 验证 GovernanceController 的 createProposal → findAllProposals → findProposal →
 * startVote → castVote → tallyVotes 链路。
 *
 * 注意：讨论期 30 天约束意味着 startVote 在测试中会失败（提案刚创建），
 * 测试验证该约束是否正确工作。
 *
 * 关联：PR #62 维护者建议 #1——补充端点契约验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GovernanceApiTest {

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
    fun `create proposal returns proposal in DISCUSSION status`() {
        val body = """
            {"title":"测试提案-调整分账比例","description":"将劳动者所得从80%提升至85%","proposedBy":"member-1","type":"SETTLEMENT_RULE"}
        """.trimIndent()
        val resp = post("/api/v1/governance/proposals", body)
        assertEquals(200, resp.statusCode(), "createProposal 应返回 200，实际: ${resp.statusCode()} body=${resp.body()}")

        val json = objectMapper.readTree(resp.body())
        assertEquals("DISCUSSION", json["status"].asText())
        assertEquals("测试提案-调整分账比例", json["title"].asText())
        assertEquals("SETTLEMENT_RULE", json["type"].asText())
        assertTrue(json["id"].asText().isNotBlank())
        assertTrue(json["discussionDeadline"].asText().isNotBlank(), "应有讨论截止日期")
    }

    @Test
    fun `find all proposals returns list including newly created`() {
        post("/api/v1/governance/proposals",
            """{"title":"测试-列表查询","description":"描述","proposedBy":"m2","type":"OTHER"}""")

        val resp = get("/api/v1/governance/proposals")
        assertEquals(200, resp.statusCode())
        val proposals = objectMapper.readTree(resp.body())
        assertTrue(proposals.size() >= 1)
        assertTrue(proposals.any { it["title"].asText() == "测试-列表查询" })
    }

    @Test
    fun `find proposal by id returns details`() {
        val createResp = post("/api/v1/governance/proposals",
            """{"title":"测试-详情查询","description":"详情描述","proposedBy":"m3","type":"POLICY_CHANGE"}""")
        val proposalId = objectMapper.readTree(createResp.body())["id"].asText()

        val resp = get("/api/v1/governance/proposals/$proposalId")
        assertEquals(200, resp.statusCode())
        val json = objectMapper.readTree(resp.body())
        assertEquals(proposalId, json["id"].asText())
        assertEquals("POLICY_CHANGE", json["type"].asText())
        assertEquals("m3", json["proposedBy"].asText())
    }

    @Test
    fun `start vote before discussion deadline returns error`() {
        // Create proposal (discussion deadline is 30 days in the future)
        val createResp = post("/api/v1/governance/proposals",
            """{"title":"测试-投票约束","description":"测试讨论期约束","proposedBy":"m4","type":"OTHER"}""")
        val proposalId = objectMapper.readTree(createResp.body())["id"].asText()

        // Try to start vote immediately → should fail (discussion period not over)
        val voteResp = post("/api/v1/governance/proposals/$proposalId/start-vote", "")
        // GlobalExceptionHandler maps IllegalArgumentException → 400 Bad Request
        assertEquals(400, voteResp.statusCode(), "讨论期未满时 startVote 应返回 400（GlobalExceptionHandler 已生效）")
    }

    @Test
    fun `tally votes on proposal with no votes returns not passed`() {
        val createResp = post("/api/v1/governance/proposals",
            """{"title":"测试-空计票","description":"无投票的计票","proposedBy":"m5","type":"OTHER"}""")
        val proposalId = objectMapper.readTree(createResp.body())["id"].asText()

        // tallyVotes works regardless of voting status (it just counts)
        val resp = post("/api/v1/governance/proposals/$proposalId/tally", "")
        assertEquals(200, resp.statusCode(), "tallyVotes 应返回 200，实际: ${resp.statusCode()} body=${resp.body()}")

        val json = objectMapper.readTree(resp.body())
        assertEquals(proposalId, json["proposalId"].asText())
        assertEquals(0, json["totalVotes"].asInt())
        org.junit.jupiter.api.Assertions.assertFalse(json["passed"].asBoolean(), "无投票时提案不应通过")
        assertTrue(json["breakdown"].asText().contains("提案未通过"))
    }

    @Test
    fun `cast vote on non-voting proposal returns error`() {
        val createResp = post("/api/v1/governance/proposals",
            """{"title":"测试-投票约束2","description":"测试投票阶段约束","proposedBy":"m6","type":"OTHER"}""")
        val proposalId = objectMapper.readTree(createResp.body())["id"].asText()

        // Try to vote before startVote → proposal is in DISCUSSION, not VOTING
        val voteBody = """{"voterId":"voter-1","stakeholderType":"WORKER","choice":"YES"}"""
        val voteResp = post("/api/v1/governance/proposals/$proposalId/vote", voteBody)
        assertEquals(400, voteResp.statusCode(), "讨论阶段投票应返回 400（GlobalExceptionHandler 已生效）")
    }

    @Test
    fun `create proposal with charter amendment type has 45 day discussion`() {
        val createResp = post("/api/v1/governance/proposals",
            """{"title":"测试-章程修改","description":"修改章程某条款","proposedBy":"m7","type":"CHARTER_AMENDMENT"}""")
        assertEquals(200, createResp.statusCode())

        val json = objectMapper.readTree(createResp.body())
        assertEquals("CHARTER_AMENDMENT", json["type"].asText())

        // Verify discussion deadline is at least 45 days from now
        val deadline = java.time.Instant.parse(json["discussionDeadline"].asText())
        val minDeadline = java.time.Instant.now().plus(44, java.time.temporal.ChronoUnit.DAYS)
        assertTrue(deadline.isAfter(minDeadline), "章程修改讨论期应至少 45 天")
    }
}
