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
 * 调度引擎 HTTP 层集成测试（#63 follow-up to PR #62）
 *
 * 验证 DispatchController 的 assignTask → findTask → findTasksByWorker →
 * savePreferences → findPreferences → optimizeRoute 链路。
 *
 * 关联：PR #62 维护者建议 #1——补充端点契约验证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DispatchApiTest {

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
    void assignTaskPersistsAndReturnsTaskDetails() throws Exception {
        String body = "{\"workerId\":\"w-disp-1\",\"serviceType\":\"RIDE_HAILING\",\"pickups\":[{\"lat\":39.9042,\"lng\":116.4074}],\"dropoffs\":[{\"lat\":39.9150,\"lng\":116.4040}],\"estimatedDistanceMeters\":5000.0,\"estimatedDurationMinutes\":20}";
        HttpResponse<String> resp = post("/api/v1/dispatch/tasks", body);
        assertEquals(200, resp.statusCode(), "assignTask 应返回 200，实际: " + resp.statusCode() + " body=" + resp.body());

        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals("w-disp-1", json.get("workerId").asText());
        assertEquals("RIDE_HAILING", json.get("serviceType").asText());
        assertEquals(1, json.get("pickupCount").asInt());
        assertEquals(1, json.get("dropoffCount").asInt());
        assertTrue(!json.get("id").asText().isBlank());
    }

    @Test
    void findTaskByIdReturnsPersistedTask() throws Exception {
        // assign first
        HttpResponse<String> assignResp = post("/api/v1/dispatch/tasks",
                "{\"id\":\"task-find-1\",\"workerId\":\"w-find\",\"serviceType\":\"FOOD_DELIVERY\",\"pickups\":[{\"lat\":30.5728,\"lng\":104.0668}],\"dropoffs\":[{\"lat\":30.5828,\"lng\":104.0768}]}");
        String taskId = objectMapper.readTree(assignResp.body()).get("id").asText();

        HttpResponse<String> resp = get("/api/v1/dispatch/tasks/" + taskId);
        assertEquals(200, resp.statusCode());
        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals(taskId, json.get("id").asText());
        assertEquals("w-find", json.get("workerId").asText());
        assertEquals("FOOD_DELIVERY", json.get("serviceType").asText());
    }

    @Test
    void findTasksByWorkerReturnsOnlyThatWorkerTasks() throws Exception {
        post("/api/v1/dispatch/tasks",
                "{\"id\":\"task-w-a\",\"workerId\":\"w-multi-disp\",\"serviceType\":\"RIDE_HAILING\",\"pickups\":[{\"lat\":30.0,\"lng\":104.0}],\"dropoffs\":[{\"lat\":30.1,\"lng\":104.1}]}");
        post("/api/v1/dispatch/tasks",
                "{\"id\":\"task-w-b\",\"workerId\":\"w-multi-disp\",\"serviceType\":\"FOOD_DELIVERY\",\"pickups\":[{\"lat\":31.0,\"lng\":105.0}],\"dropoffs\":[{\"lat\":31.1,\"lng\":105.1}]}");
        post("/api/v1/dispatch/tasks",
                "{\"id\":\"task-w-c\",\"workerId\":\"w-other-disp\",\"serviceType\":\"RIDE_HAILING\",\"pickups\":[{\"lat\":32.0,\"lng\":106.0}],\"dropoffs\":[{\"lat\":32.1,\"lng\":106.1}]}");

        HttpResponse<String> resp = get("/api/v1/dispatch/workers/w-multi-disp/tasks");
        assertEquals(200, resp.statusCode());
        JsonNode tasks = objectMapper.readTree(resp.body());
        assertEquals(2, tasks.size());
        assertTrue(iterableAllMatch(tasks, n -> n.get("workerId").asText().equals("w-multi-disp")));
    }

    @Test
    void saveAndFindPreferencesRoundTripsAllFields() throws Exception {
        String body = "{\"preferredServiceTypes\":[\"RIDE_HAILING\",\"FOOD_DELIVERY\"],\"preferredRegions\":[\"chengdu_wuhou\"],\"excludedRegions\":[\"chengdu_jinjiang\"],\"preferredTimeSlots\":[{\"dayOfWeek\":1,\"startHour\":8,\"endHour\":12}],\"excludedTimeSlots\":[{\"dayOfWeek\":7,\"startHour\":0,\"endHour\":6}],\"maxConcurrentOrders\":2,\"maxDailyHours\":10.0}";
        HttpResponse<String> resp = post("/api/v1/dispatch/workers/w-pref-1/preferences", body);
        assertEquals(200, resp.statusCode(), "savePreferences 应返回 200，实际: " + resp.statusCode() + " body=" + resp.body());
        assertEquals("ok", objectMapper.readTree(resp.body()).get("status").asText());

        // verify
        HttpResponse<String> getResp = get("/api/v1/dispatch/workers/w-pref-1/preferences");
        assertEquals(200, getResp.statusCode());
        JsonNode prefs = objectMapper.readTree(getResp.body());
        assertEquals("w-pref-1", prefs.get("workerId").asText());
        assertEquals(2, prefs.get("preferredServiceTypes").size());
        assertEquals(2, prefs.get("maxConcurrentOrders").asInt());
        assertEquals(10.0, prefs.get("maxDailyHours").asDouble(), 0.01);
        assertEquals(1, prefs.get("preferredTimeSlots").size());
    }

    @Test
    void findNonExistentPreferencesReturnsNullBody() throws Exception {
        HttpResponse<String> resp = get("/api/v1/dispatch/workers/no-such-worker/preferences");
        assertEquals(200, resp.statusCode());
        // Returns null from controller → empty body or null JSON
        assertTrue(resp.body() == null || resp.body().isBlank() || "null".equals(resp.body()));
    }

    @Test
    void optimizeRouteReturnsOrderedWaypoints() throws Exception {
        String body = "{\"workerId\":\"w-route-1\",\"serviceType\":\"FOOD_DELIVERY\",\"currentLat\":39.9042,\"currentLng\":116.4074,\"pickups\":[{\"lat\":39.9100,\"lng\":116.4100},{\"lat\":39.9200,\"lng\":116.4200}],\"dropoffs\":[{\"lat\":39.9300,\"lng\":116.4300}]}";
        HttpResponse<String> resp = post("/api/v1/dispatch/optimize-route", body);
        assertEquals(200, resp.statusCode(), "optimizeRoute 应返回 200，实际: " + resp.statusCode() + " body=" + resp.body());

        JsonNode json = objectMapper.readTree(resp.body());
        assertEquals("w-route-1", json.get("workerId").asText());
        assertEquals(3, json.get("orderedWaypoints").size(), "应有 3 个途经点");
        assertTrue(json.get("totalDistanceMeters").asDouble() > 0);
        assertTrue(json.get("estimatedTotalMinutes").asInt() > 0);
        assertTrue(json.get("reason").asText().contains("劳动者效率"));
    }

    @Test
    void assignTaskWithAutoGeneratedIdWorks() throws Exception {
        String body = "{\"workerId\":\"w-auto-id\",\"serviceType\":\"ERRAND\",\"pickups\":[{\"lat\":30.5,\"lng\":104.0}],\"dropoffs\":[{\"lat\":30.6,\"lng\":104.1}]}";
        HttpResponse<String> resp = post("/api/v1/dispatch/tasks", body);
        assertEquals(200, resp.statusCode());
        JsonNode json = objectMapper.readTree(resp.body());
        String taskId = json.get("id").asText();
        assertTrue(!taskId.isBlank(), "自动生成的 ID 不应为空");

        // verify can be found
        HttpResponse<String> getResp = get("/api/v1/dispatch/tasks/" + taskId);
        assertEquals(200, getResp.statusCode());
    }

    private interface JsonPredicate {
        boolean test(JsonNode node);
    }

    private static boolean iterableAllMatch(JsonNode array, JsonPredicate predicate) {
        for (JsonNode node : array) {
            if (!predicate.test(node)) {
                return false;
            }
        }
        return true;
    }
}
