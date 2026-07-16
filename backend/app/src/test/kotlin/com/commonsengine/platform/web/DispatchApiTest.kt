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
 * 调度引擎 HTTP 层集成测试（#63 follow-up to PR #62）
 *
 * 验证 DispatchController 的 assignTask → findTask → findTasksByWorker →
 * savePreferences → findPreferences → optimizeRoute 链路。
 *
 * 关联：PR #62 维护者建议 #1——补充端点契约验证。
 * 关注点：GeoPoint / List 序列化在 dispatch 模块中使用了 JSON 字符串策略，
 * 需验证 HTTP 层反序列化不报错。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DispatchApiTest {

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
    fun `assign task persists and returns task details`() {
        val body = """
            {"workerId":"w-disp-1","serviceType":"RIDE_HAILING","pickups":[{"lat":39.9042,"lng":116.4074}],"dropoffs":[{"lat":39.9150,"lng":116.4040}],"estimatedDistanceMeters":5000.0,"estimatedDurationMinutes":20}
        """.trimIndent()
        val resp = post("/api/v1/dispatch/tasks", body)
        assertEquals(200, resp.statusCode(), "assignTask 应返回 200，实际: ${resp.statusCode()} body=${resp.body()}")

        val json = objectMapper.readTree(resp.body())
        assertEquals("w-disp-1", json["workerId"].asText())
        assertEquals("RIDE_HAILING", json["serviceType"].asText())
        assertEquals(1, json["pickupCount"].asInt())
        assertEquals(1, json["dropoffCount"].asInt())
        assertTrue(json["id"].asText().isNotBlank())
    }

    @Test
    fun `find task by id returns persisted task`() {
        // assign first
        val assignResp = post("/api/v1/dispatch/tasks",
            """{"id":"task-find-1","workerId":"w-find","serviceType":"FOOD_DELIVERY","pickups":[{"lat":30.5728,"lng":104.0668}],"dropoffs":[{"lat":30.5828,"lng":104.0768}]}""")
        val taskId = objectMapper.readTree(assignResp.body())["id"].asText()

        val resp = get("/api/v1/dispatch/tasks/$taskId")
        assertEquals(200, resp.statusCode())
        val json = objectMapper.readTree(resp.body())
        assertEquals(taskId, json["id"].asText())
        assertEquals("w-find", json["workerId"].asText())
        assertEquals("FOOD_DELIVERY", json["serviceType"].asText())
    }

    @Test
    fun `find tasks by worker returns only that worker tasks`() {
        post("/api/v1/dispatch/tasks",
            """{"id":"task-w-a","workerId":"w-multi-disp","serviceType":"RIDE_HAILING","pickups":[{"lat":30.0,"lng":104.0}],"dropoffs":[{"lat":30.1,"lng":104.1}]}""")
        post("/api/v1/dispatch/tasks",
            """{"id":"task-w-b","workerId":"w-multi-disp","serviceType":"FOOD_DELIVERY","pickups":[{"lat":31.0,"lng":105.0}],"dropoffs":[{"lat":31.1,"lng":105.1}]}""")
        post("/api/v1/dispatch/tasks",
            """{"id":"task-w-c","workerId":"w-other-disp","serviceType":"RIDE_HAILING","pickups":[{"lat":32.0,"lng":106.0}],"dropoffs":[{"lat":32.1,"lng":106.1}]}""")

        val resp = get("/api/v1/dispatch/workers/w-multi-disp/tasks")
        assertEquals(200, resp.statusCode())
        val tasks = objectMapper.readTree(resp.body())
        assertEquals(2, tasks.size())
        assertTrue(tasks.all { it["workerId"].asText() == "w-multi-disp" })
    }

    @Test
    fun `save and find preferences round-trips all fields`() {
        val body = """
            {"preferredServiceTypes":["RIDE_HAILING","FOOD_DELIVERY"],"preferredRegions":["chengdu_wuhou"],"excludedRegions":["chengdu_jinjiang"],"preferredTimeSlots":[{"dayOfWeek":1,"startHour":8,"endHour":12}],"excludedTimeSlots":[{"dayOfWeek":7,"startHour":0,"endHour":6}],"maxConcurrentOrders":2,"maxDailyHours":10.0}
        """.trimIndent()
        val resp = post("/api/v1/dispatch/workers/w-pref-1/preferences", body)
        assertEquals(200, resp.statusCode(), "savePreferences 应返回 200，实际: ${resp.statusCode()} body=${resp.body()}")
        assertEquals("ok", objectMapper.readTree(resp.body())["status"].asText())

        // verify
        val getResp = get("/api/v1/dispatch/workers/w-pref-1/preferences")
        assertEquals(200, getResp.statusCode())
        val prefs = objectMapper.readTree(getResp.body())
        assertEquals("w-pref-1", prefs["workerId"].asText())
        assertEquals(2, prefs["preferredServiceTypes"].size())
        assertEquals(2, prefs["maxConcurrentOrders"].asInt())
        assertEquals(10.0, prefs["maxDailyHours"].asDouble(), 0.01)
        assertEquals(1, prefs["preferredTimeSlots"].size())
    }

    @Test
    fun `find non-existent preferences returns null body`() {
        val resp = get("/api/v1/dispatch/workers/no-such-worker/preferences")
        assertEquals(200, resp.statusCode())
        // Returns null from controller → empty body or null JSON
        assertTrue(resp.body().isBlank() || resp.body() == "null")
    }

    @Test
    fun `optimize route returns ordered waypoints`() {
        val body = """
            {"workerId":"w-route-1","serviceType":"FOOD_DELIVERY","currentLat":39.9042,"currentLng":116.4074,"pickups":[{"lat":39.9100,"lng":116.4100},{"lat":39.9200,"lng":116.4200}],"dropoffs":[{"lat":39.9300,"lng":116.4300}]}
        """.trimIndent()
        val resp = post("/api/v1/dispatch/optimize-route", body)
        assertEquals(200, resp.statusCode(), "optimizeRoute 应返回 200，实际: ${resp.statusCode()} body=${resp.body()}")

        val json = objectMapper.readTree(resp.body())
        assertEquals("w-route-1", json["workerId"].asText())
        assertEquals(3, json["orderedWaypoints"].size(), "应有 3 个途经点")
        assertTrue(json["totalDistanceMeters"].asDouble() > 0)
        assertTrue(json["estimatedTotalMinutes"].asInt() > 0)
        assertTrue(json["reason"].asText().contains("劳动者效率"))
    }

    @Test
    fun `assign task with auto-generated id works`() {
        val body = """
            {"workerId":"w-auto-id","serviceType":"ERRAND","pickups":[{"lat":30.5,"lng":104.0}],"dropoffs":[{"lat":30.6,"lng":104.1}]}
        """.trimIndent()
        val resp = post("/api/v1/dispatch/tasks", body)
        assertEquals(200, resp.statusCode())
        val json = objectMapper.readTree(resp.body())
        val taskId = json["id"].asText()
        assertTrue(taskId.isNotBlank(), "自动生成的 ID 不应为空")

        // verify can be found
        val getResp = get("/api/v1/dispatch/tasks/$taskId")
        assertEquals(200, getResp.statusCode())
    }
}
