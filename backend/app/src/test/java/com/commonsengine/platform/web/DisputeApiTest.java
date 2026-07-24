package com.commonsengine.platform.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void fileDisputeCreatesAndReturnsDisputeWithFiledStatus() throws Exception {
        String body = "{\"transactionId\":\"tx-d1\",\"filedBy\":\"c-d1\",\"filedAgainst\":\"w-d1\",\"type\":\"FARE_DISPUTE\",\"description\":\"多收了10元\",\"evidenceUrls\":[\"https://example.com/evidence1.png\"]}";
        HttpResponse<String> resp = post("/api/v1/dispute/file", body);
        assertEquals(200, resp.statusCode(), "file 应返回 200，实际: " + resp.statusCode() + " body=" + resp.body());

        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals("FILED", json.get("status").asText());
        assertEquals("FARE_DISPUTE", json.get("type").asText());
        assertEquals("tx-d1", json.get("transactionId").asText());
        assertTrue(!json.get("id").asText().isBlank());
    }

    @Test
    void aiScreeningClassifiesAndReturnsPriority() throws Exception {
        // file a dispute first
        HttpResponse<String> fileResp = post("/api/v1/dispute/file",
                "{\"transactionId\":\"tx-d2\",\"filedBy\":\"c-d2\",\"filedAgainst\":\"w-d2\",\"type\":\"BEHAVIORAL\",\"description\":\"司机态度恶劣\",\"evidenceUrls\":[]}");
        String disputeId = objectMapper.readTree(fileResp.body()).get("id").asText();

        // ai screening
        HttpResponse<String> screenResp = post("/api/v1/dispute/" + disputeId + "/screening", "");
        assertEquals(200, screenResp.statusCode(), "screening 应返回 200，实际: " + screenResp.statusCode() + " body=" + screenResp.body());

        JsonNode json = objectMapper.readTree(screenResp.body());
        assertEquals(disputeId, json.get("disputeId").asText());
        // BEHAVIORAL → HIGH priority
        assertEquals("HIGH", json.get("suggestedPriority").asText());
        assertTrue(json.get("confidence").asDouble() > 0);
        assertTrue(!json.get("reasoning").asText().isBlank());
    }

    @Test
    void arbitrateResolvesDisputeWithVerdict() throws Exception {
        // file + screen first
        HttpResponse<String> fileResp = post("/api/v1/dispute/file",
                "{\"transactionId\":\"tx-d3\",\"filedBy\":\"c-d3\",\"filedAgainst\":\"w-d3\",\"type\":\"FARE_DISPUTE\",\"description\":\"费用不符\",\"evidenceUrls\":[]}");
        String disputeId = objectMapper.readTree(fileResp.body()).get("id").asText();

        // ai screening to move to INVESTIGATION
        post("/api/v1/dispute/" + disputeId + "/screening", "");

        // arbitrate
        String arbBody = "{\"verdict\":\"FAVOR_FILER\",\"reasoning\":\"证据显示多收费用\",\"compensationAmount\":\"10.00\"}";
        HttpResponse<String> arbResp = post("/api/v1/dispute/" + disputeId + "/arbitrate", arbBody);
        assertEquals(200, arbResp.statusCode(), "arbitrate 应返回 200，实际: " + arbResp.statusCode() + " body=" + arbResp.body());

        JsonNode json = objectMapper.readTree(arbResp.body());
        assertEquals("FAVOR_FILER", json.get("verdict").asText());
        assertEquals("10.00", json.get("compensationAmount").asText());
        assertTrue(!json.get("decidedAt").asText().isBlank());
    }

    @Test
    void findByIdReturnsDisputeDetails() throws Exception {
        HttpResponse<String> fileResp = post("/api/v1/dispute/file",
                "{\"transactionId\":\"tx-d4\",\"filedBy\":\"c-d4\",\"filedAgainst\":\"w-d4\",\"type\":\"SERVICE_QUALITY\",\"description\":\"服务差\",\"evidenceUrls\":[]}");
        String disputeId = objectMapper.readTree(fileResp.body()).get("id").asText();

        HttpResponse<String> resp = get("/api/v1/dispute/" + disputeId);
        assertEquals(200, resp.statusCode());
        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals(disputeId, json.get("id").asText());
        assertEquals("SERVICE_QUALITY", json.get("type").asText());
    }

    @Test
    void findAllReturnsListOfDisputes() throws Exception {
        // ensure at least one dispute exists
        post("/api/v1/dispute/file",
                "{\"transactionId\":\"tx-d5\",\"filedBy\":\"c-d5\",\"filedAgainst\":\"w-d5\",\"type\":\"OTHER\",\"description\":\"测试工单\",\"evidenceUrls\":[]}");

        HttpResponse<String> resp = get("/api/v1/dispute");
        assertEquals(200, resp.statusCode());
        JsonNode disputes = objectMapper.readTree(resp.body());
        assertTrue(disputes.size() >= 1, "应至少有 1 个工单");
    }

    @Test
    void findAllWithStatusFilterWorks() throws Exception {
        // file + screen → ends up in INVESTIGATION status
        HttpResponse<String> fileResp = post("/api/v1/dispute/file",
                "{\"transactionId\":\"tx-d6\",\"filedBy\":\"c-d6\",\"filedAgainst\":\"w-d6\",\"type\":\"FARE_DISPUTE\",\"description\":\"费用争议\",\"evidenceUrls\":[]}");
        String disputeId = objectMapper.readTree(fileResp.body()).get("id").asText();
        post("/api/v1/dispute/" + disputeId + "/screening", "");

        // query by status=INVESTIGATION
        HttpResponse<String> resp = get("/api/v1/dispute?status=INVESTIGATION");
        assertEquals(200, resp.statusCode());
        JsonNode disputes = objectMapper.readTree(resp.body());
        assertTrue(disputes.size() >= 1);
        for (JsonNode d : disputes) {
            assertTrue(d.get("status").asText().equals("INVESTIGATION"));
        }
    }
}
