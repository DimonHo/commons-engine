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
 * 匹配引擎 HTTP 层回归测试（真实 Tomcat + JDK HttpClient，H2 test profile）。
 *
 * 为什么需要：服务层单元测试 / e2e（EndToEndFlowTest）直接调用 service，绕过 HTTP+Jackson；
 * 这会掩盖 HTTP 反序列化缺陷（如 Jackson 3.x Kotlin 模块缺失导致 POST 体无法解析——
 * demo.yml smoke-test 曾因此失败）。本测试起真实 Web 服务器，走完整 HTTP 路径，
 * 复刻 smoke-test.sh 关键链路：注册 → 位置上报 → 自动匹配 → 健康检查。
 *
 * 关联：demo 可运行验证；防止 Spring Boot 4.x / Jackson 3.x 迁移缺陷回归。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MatchingHttpApiTest {

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

    private fun register(phone: String, role: String): String {
        val body = """{"name":"测试用户","phone":"$phone","roles":["$role"]}"""
        val resp = post("/api/v1/members/register", body)
        assertEquals(200, resp.statusCode(), "注册应返回 200，实际: ${resp.statusCode()} body=${resp.body()}")
        return objectMapper.readTree(resp.body())["id"].asText()
    }

    @Test
    fun `smoke-test flow over HTTP - register, location, auto-match`() {
        // 1. 注册劳动者（验证 Jackson Kotlin 模块可用——Kotlin data class 反序列化）
        val workerId = register("13700000999", "WORKER")
        assertTrue(workerId.isNotBlank(), "劳动者注册应返回 id")

        // 2. 劳动者上报位置（POST 体含 List 与可选字段——Jackson 模块缺失时的典型失败点）
        val locBody = """
            {"name":"测试骑手","lat":39.9850,"lng":116.3080,"serviceTypes":["RIDE_HAILING"],"rating":4.9}
        """.trimIndent()
        val locResp = post("/api/v1/matching/workers/$workerId/location", locBody)
        assertEquals(200, locResp.statusCode(), "位置上报应 200，实际: ${locResp.statusCode()} body=${locResp.body()}")
        assertEquals("ok", objectMapper.readTree(locResp.body())["status"].asText())

        // 3. 自动匹配（AutoMatchRequest 反序列化 + DB 地理检索 + 引擎）
        val matchBody = """
            {"consumerId":"consumer-smoke","serviceType":"RIDE_HAILING","pickupLat":39.9847,"pickupLng":116.3076,"radiusMeters":5000}
        """.trimIndent()
        val matchResp = post("/api/v1/matching/match/auto", matchBody)
        assertEquals(200, matchResp.statusCode(), "自动匹配应 200，实际: ${matchResp.statusCode()} body=${matchResp.body()}")
        assertTrue(objectMapper.readTree(matchResp.body())["matched"].asBoolean(), "应匹配成功")

        // 4. 健康检查（GET）
        val healthResp = get("/api/v1/matching/health")
        assertEquals(200, healthResp.statusCode())
        assertTrue(healthResp.body().contains("availableStrategies"))
    }
}
