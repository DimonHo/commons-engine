package com.commonsengine.platform.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void createProposalReturnsProposalInDiscussionStatus() throws Exception {
        String body = "{\"title\":\"测试提案-调整分账比例\",\"description\":\"将劳动者所得从80%提升至85%\",\"proposedBy\":\"member-1\",\"type\":\"SETTLEMENT_RULE\"}";
        HttpResponse<String> resp = post("/api/v1/governance/proposals", body);
        assertEquals(200, resp.statusCode(), "createProposal 应返回 200，实际: " + resp.statusCode() + " body=" + resp.body());

        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals("DISCUSSION", json.get("status").asText());
        assertEquals("测试提案-调整分账比例", json.get("title").asText());
        assertEquals("SETTLEMENT_RULE", json.get("type").asText());
        assertTrue(!json.get("id").asText().isBlank());
        assertTrue(!json.get("discussionDeadline").asText().isBlank(), "应有讨论截止日期");
    }

    @Test
    void findAllProposalsReturnsListIncludingNewlyCreated() throws Exception {
        post("/api/v1/governance/proposals",
                "{\"title\":\"测试-列表查询\",\"description\":\"描述\",\"proposedBy\":\"m2\",\"type\":\"OTHER\"}");

        HttpResponse<String> resp = get("/api/v1/governance/proposals");
        assertEquals(200, resp.statusCode());
        JsonNode proposals = objectMapper.readTree(resp.body());
        assertTrue(proposals.size() >= 1);
        boolean found = false;
        for (JsonNode p : proposals) {
            if (p.get("title").asText().equals("测试-列表查询")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "列表应包含新创建的提案");
    }

    @Test
    void findProposalByIdReturnsDetails() throws Exception {
        HttpResponse<String> createResp = post("/api/v1/governance/proposals",
                "{\"title\":\"测试-详情查询\",\"description\":\"详情描述\",\"proposedBy\":\"m3\",\"type\":\"POLICY_CHANGE\"}");
        String proposalId = objectMapper.readTree(createResp.body()).get("id").asText();

        HttpResponse<String> resp = get("/api/v1/governance/proposals/" + proposalId);
        assertEquals(200, resp.statusCode());
        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals(proposalId, json.get("id").asText());
        assertEquals("POLICY_CHANGE", json.get("type").asText());
        assertEquals("m3", json.get("proposedBy").asText());
    }

    @Test
    void startVoteBeforeDiscussionDeadlineReturnsError() throws Exception {
        // Create proposal (discussion deadline is 30 days in the future)
        HttpResponse<String> createResp = post("/api/v1/governance/proposals",
                "{\"title\":\"测试-投票约束\",\"description\":\"测试讨论期约束\",\"proposedBy\":\"m4\",\"type\":\"OTHER\"}");
        String proposalId = objectMapper.readTree(createResp.body()).get("id").asText();

        // Try to start vote immediately → should fail (discussion period not over)
        HttpResponse<String> voteResp = post("/api/v1/governance/proposals/" + proposalId + "/start-vote", "");
        // GlobalExceptionHandler maps IllegalArgumentException → 400 Bad Request
        assertEquals(400, voteResp.statusCode(), "讨论期未满时 startVote 应返回 400（GlobalExceptionHandler 已生效）");
    }

    @Test
    void tallyVotesOnProposalWithNoVotesReturnsNotPassed() throws Exception {
        HttpResponse<String> createResp = post("/api/v1/governance/proposals",
                "{\"title\":\"测试-空计票\",\"description\":\"无投票的计票\",\"proposedBy\":\"m5\",\"type\":\"OTHER\"}");
        String proposalId = objectMapper.readTree(createResp.body()).get("id").asText();

        // tallyVotes works regardless of voting status (it just counts)
        HttpResponse<String> resp = post("/api/v1/governance/proposals/" + proposalId + "/tally", "");
        assertEquals(200, resp.statusCode(), "tallyVotes 应返回 200，实际: " + resp.statusCode() + " body=" + resp.body());

        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals(proposalId, json.get("proposalId").asText());
        assertEquals(0, json.get("totalVotes").asInt());
        assertFalse(json.get("passed").asBoolean(), "无投票时提案不应通过");
        assertTrue(json.get("breakdown").asText().contains("提案未通过"));
    }

    @Test
    void castVoteOnNonVotingProposalReturnsError() throws Exception {
        HttpResponse<String> createResp = post("/api/v1/governance/proposals",
                "{\"title\":\"测试-投票约束2\",\"description\":\"测试投票阶段约束\",\"proposedBy\":\"m6\",\"type\":\"OTHER\"}");
        String proposalId = objectMapper.readTree(createResp.body()).get("id").asText();

        // Try to vote before startVote → proposal is in DISCUSSION, not VOTING
        String voteBody = "{\"voterId\":\"voter-1\",\"stakeholderType\":\"WORKER\",\"choice\":\"YES\"}";
        HttpResponse<String> voteResp = post("/api/v1/governance/proposals/" + proposalId + "/vote", voteBody);
        assertEquals(400, voteResp.statusCode(), "讨论阶段投票应返回 400（GlobalExceptionHandler 已生效）");
    }

    @Test
    void createProposalWithCharterAmendmentTypeHas45DayDiscussion() throws Exception {
        HttpResponse<String> createResp = post("/api/v1/governance/proposals",
                "{\"title\":\"测试-章程修改\",\"description\":\"修改章程某条款\",\"proposedBy\":\"m7\",\"type\":\"CHARTER_AMENDMENT\"}");
        assertEquals(200, createResp.statusCode());

        JsonNode json = objectMapper.readTree(createResp.body());
        assertEquals("CHARTER_AMENDMENT", json.get("type").asText());

        // Verify discussion deadline is at least 45 days from now
        Instant deadline = Instant.parse(json.get("discussionDeadline").asText());
        Instant minDeadline = Instant.now().plus(44, ChronoUnit.DAYS);
        assertTrue(deadline.isAfter(minDeadline), "章程修改讨论期应至少 45 天");
    }
}
