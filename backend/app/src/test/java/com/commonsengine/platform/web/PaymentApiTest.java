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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private HttpResponse<String> postEmpty(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
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

    private String charge(String consumerId, String workerId, String amount, String serviceType) throws Exception {
        String body = "{\"consumerId\":\"" + consumerId + "\",\"workerId\":\"" + workerId
                + "\",\"amount\":\"" + amount + "\",\"serviceType\":\"" + serviceType + "\"}";
        HttpResponse<String> resp = post("/api/v1/payment/charge", body);
        assertEquals(200, resp.statusCode(), "charge should return 200: " + resp.statusCode() + " body=" + resp.body());
        return objectMapper.readTree(resp.body()).get("id").asText();
    }

    private String charge() throws Exception {
        return charge("consumer-test", "worker-test", "100.00", "RIDE_HAILING");
    }

    @Test
    void chargeCreatesTransactionAndReturnsChargedStatus() throws Exception {
        String txId = charge("consumer-test", "worker-test", "35.50", "RIDE_HAILING");
        assertTrue(!txId.isBlank(), "should return transaction ID");
    }

    @Test
    void settleLoadsTransactionFromEventStoreNoClientSuppliedFields() throws Exception {
        // 1. charge
        String txId = charge("c2", "w2", "100.00", "RIDE_HAILING");

        // 2. settle with NO body fields (just transactionId in path)
        HttpResponse<String> settleResp = postEmpty("/api/v1/payment/" + txId + "/settle");
        assertEquals(200, settleResp.statusCode(), "settle should return 200: " + settleResp.statusCode() + " body=" + settleResp.body());

        JsonNode settleJson = objectMapper.readTree(settleResp.body());
        assertEquals("100.00", settleJson.get("totalAmount").asText());
        // Default rule: 80/15/5
        assertEquals("80.00", settleJson.get("workerPayout").asText());
        assertEquals("15.00", settleJson.get("platformFee").asText());
        assertEquals("5.00", settleJson.get("commonsFund").asText());
        assertTrue(settleJson.get("breakdown").asText().contains("劳动者"),
                "breakdown 应包含劳动者分账信息: " + settleJson.get("breakdown").asText());
    }

    @Test
    void settleReturns404ForNonExistentTransaction() throws Exception {
        HttpResponse<String> resp = postEmpty("/api/v1/payment/nonexistent-tx-id/settle");
        assertEquals(404, resp.statusCode(), "should return 404: " + resp.statusCode());
        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals("NOT_FOUND", json.get("error").asText());
    }

    @Test
    void refundLoadsTransactionFromEventStoreOnlyNeedsReason() throws Exception {
        String txId = charge("c4", "w4", "50.00", "RIDE_HAILING");

        // refund only sends reason - no transaction fields
        HttpResponse<String> refundResp = post("/api/v1/payment/" + txId + "/refund", "{\"reason\":\"user cancelled\"}");
        assertEquals(200, refundResp.statusCode());
        JsonNode refundJson = objectMapper.readTree(refundResp.body());
        assertTrue(refundJson.get("success").asBoolean());
        assertEquals(txId, refundJson.get("transactionId").asText());
    }

    @Test
    void refundReturns404ForNonExistentTransaction() throws Exception {
        HttpResponse<String> resp = post("/api/v1/payment/nonexistent-tx-id/refund", "{\"reason\":\"test\"}");
        assertEquals(404, resp.statusCode());
    }

    @Test
    void historyReturnsAllLedgerEventsForATransaction() throws Exception {
        String txId = charge("c5", "w5", "80.00", "RIDE_HAILING");
        postEmpty("/api/v1/payment/" + txId + "/settle");

        HttpResponse<String> historyResp = get("/api/v1/payment/" + txId + "/history");
        assertEquals(200, historyResp.statusCode());
        JsonNode events = objectMapper.readTree(historyResp.body());
        assertTrue(events.size() >= 2, "should have at least charge + settle events");
        boolean hasCharge = false;
        boolean hasSettle = false;
        for (JsonNode e : events) {
            String type = e.get("type").asText();
            if (type.equals("CHARGE_CREATED")) hasCharge = true;
            if (type.equals("SETTLEMENT_COMPLETED")) hasSettle = true;
        }
        assertTrue(hasCharge, "应有 CHARGE_CREATED 事件");
        assertTrue(hasSettle, "应有 SETTLEMENT_COMPLETED 事件");
    }

    @Test
    void clientCannotOverrideSettlementRateViaApi() throws Exception {
        // After P0 fix: SettleRequest DTO no longer exists.
        // Even if client sends workerRate in body, it's ignored.
        String txId = charge("c6", "w6", "200.00", "RIDE_HAILING");

        // Attempt to inject custom rates (should be ignored)
        String maliciousBody = "{\"workerRate\":0.50,\"operationRate\":0.40,\"commonsRate\":0.10}";
        HttpResponse<String> settleResp = post("/api/v1/payment/" + txId + "/settle", maliciousBody);
        assertEquals(200, settleResp.statusCode());

        JsonNode settleJson = objectMapper.readTree(settleResp.body());
        // Should use DEFAULT rule (80%), not the injected 50%
        assertEquals("160.00", settleJson.get("workerPayout").asText());  // 200 * 0.80
        assertFalse(settleJson.get("workerPayout").asText().equals("100.00")); // NOT 200 * 0.50
    }
}
