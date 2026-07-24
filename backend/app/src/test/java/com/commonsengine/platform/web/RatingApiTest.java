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
 * 信用评价 HTTP 层集成测试（#63 follow-up to PR #62）
 *
 * 验证 RatingController 的 submit → findReceived → findGiven → findByTransaction →
 * getCreditProfile → exportProfile 链路。
 *
 * 关联：PR #62 维护者建议 #1——补充端点契约验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RatingApiTest {

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
    void submitRatingPersistsAndReturnsRatingData() throws Exception {
        String body = "{\"transactionId\":\"tx-1\",\"raterId\":\"consumer-1\",\"rateeId\":\"worker-1\",\"direction\":\"CONSUMER_TO_WORKER\",\"score\":5,\"tags\":[\"PUNCTUAL\",\"SAFE_DRIVING\",\"POLITE\"],\"comment\":\"服务很好\"}";
        HttpResponse<String> resp = post("/api/v1/rating/submit", body);
        assertEquals(200, resp.statusCode(), "submit 应返回 200，实际: " + resp.statusCode() + " body=" + resp.body());

        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals("tx-1", json.get("transactionId").asText());
        assertEquals("CONSUMER_TO_WORKER", json.get("direction").asText());
        assertEquals(5, json.get("score").asInt());
        assertTrue(json.get("tags").size() == 3);
        assertTrue(!json.get("id").asText().isBlank());
    }

    @Test
    void findReceivedRatingsReturnsRatingsForAMember() throws Exception {
        // Submit a rating
        post("/api/v1/rating/submit",
                "{\"transactionId\":\"tx-2\",\"raterId\":\"c2\",\"rateeId\":\"w2\",\"direction\":\"CONSUMER_TO_WORKER\",\"score\":4,\"tags\":[\"POLITE\"]}");

        HttpResponse<String> resp = get("/api/v1/rating/received/w2");
        assertEquals(200, resp.statusCode());
        JsonNode ratings = objectMapper.readTree(resp.body());
        assertTrue(ratings.size() >= 1, "应至少有 1 条收到的评价");
        boolean found = false;
        for (JsonNode r : ratings) {
            if (r.get("rateeId").asText().equals("w2")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "应包含 rateeId=w2 的评价");
    }

    @Test
    void findGivenRatingsReturnsRatingsByAMember() throws Exception {
        post("/api/v1/rating/submit",
                "{\"transactionId\":\"tx-3\",\"raterId\":\"c3\",\"rateeId\":\"w3\",\"direction\":\"CONSUMER_TO_WORKER\",\"score\":3,\"tags\":[]}");

        HttpResponse<String> resp = get("/api/v1/rating/given/c3");
        assertEquals(200, resp.statusCode());
        JsonNode ratings = objectMapper.readTree(resp.body());
        assertTrue(ratings.size() >= 1);
        boolean found = false;
        for (JsonNode r : ratings) {
            if (r.get("raterId").asText().equals("c3")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "应包含 raterId=c3 的评价");
    }

    @Test
    void findByTransactionReturnsBidirectionalRatings() throws Exception {
        // Consumer → Worker
        post("/api/v1/rating/submit",
                "{\"transactionId\":\"tx-bi\",\"raterId\":\"c-bi\",\"rateeId\":\"w-bi\",\"direction\":\"CONSUMER_TO_WORKER\",\"score\":5,\"tags\":[\"PUNCTUAL\"]}");
        // Worker → Consumer
        post("/api/v1/rating/submit",
                "{\"transactionId\":\"tx-bi\",\"raterId\":\"w-bi\",\"rateeId\":\"c-bi\",\"direction\":\"WORKER_TO_CONSUMER\",\"score\":4,\"tags\":[\"POLITE\"]}");

        HttpResponse<String> resp = get("/api/v1/rating/transaction/tx-bi");
        assertEquals(200, resp.statusCode());
        JsonNode ratings = objectMapper.readTree(resp.body());
        assertEquals(2, ratings.size(), "一笔交易应有双向评价");
    }

    @Test
    void getCreditProfileAggregatesReceivedRatings() throws Exception {
        post("/api/v1/rating/submit",
                "{\"transactionId\":\"tx-p1\",\"raterId\":\"c-p1\",\"rateeId\":\"w-profile\",\"direction\":\"CONSUMER_TO_WORKER\",\"score\":5,\"tags\":[\"PUNCTUAL\",\"SAFE_DRIVING\"]}");
        post("/api/v1/rating/submit",
                "{\"transactionId\":\"tx-p2\",\"raterId\":\"c-p2\",\"rateeId\":\"w-profile\",\"direction\":\"CONSUMER_TO_WORKER\",\"score\":3,\"tags\":[\"POLITE\"]}");

        HttpResponse<String> resp = get("/api/v1/rating/profile/w-profile");
        assertEquals(200, resp.statusCode());
        JsonNode profile = objectMapper.readTree(resp.body());
        assertEquals("w-profile", profile.get("memberId").asText());
        assertTrue(profile.get("totalRatings").asInt() >= 2);
        double avg = profile.get("averageScore").asDouble();
        assertTrue(avg >= 3.0 && avg <= 5.0, "平均分应在 3-5 之间，实际: " + avg);
    }

    @Test
    void exportProfileReturnsTextWithCreditHistory() throws Exception {
        post("/api/v1/rating/submit",
                "{\"transactionId\":\"tx-e1\",\"raterId\":\"c-e1\",\"rateeId\":\"w-export\",\"direction\":\"CONSUMER_TO_WORKER\",\"score\":5,\"tags\":[\"POLITE\"],\"comment\":\"好评\"}");

        HttpResponse<String> resp = get("/api/v1/rating/export/w-export");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("信用记录导出"), "导出应包含标题");
        assertTrue(resp.body().contains("w-export"), "导出应包含成员 ID");
        assertTrue(resp.body().contains("可携带"), "导出应包含数据携带声明");
    }
}
