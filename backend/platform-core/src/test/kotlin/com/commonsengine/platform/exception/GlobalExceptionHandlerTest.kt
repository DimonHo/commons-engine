package com.commonsengine.platform.exception

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * GlobalExceptionHandler integration test (#63).
 *
 * Validates HTTP status codes and response body structure for each
 * exception type via real HTTP (RANDOM_PORT + JDK HttpClient), matching
 * the project-wide test pattern.
 */
@SpringBootTest(
    classes = [ExceptionTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @LocalServerPort
    private var port: Int = 0

    private val http: HttpClient = HttpClient.newHttpClient()

    private fun get(path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port$path"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    /** Extract a JSON string field value by key (simple regex, avoids Jackson dependency). */
    private fun jsonField(body: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(body)?.groupValues?.getOrNull(1)
    }

    @Test
    fun `IllegalArgumentException returns 400 with BAD_REQUEST error code`() {
        val resp = get("/test/illegal-arg")
        assertEquals(400, resp.statusCode(), "should return 400")
        assertEquals("BAD_REQUEST", jsonField(resp.body(), "error"))
        assertEquals("参数不合法测试", jsonField(resp.body(), "message"))
    }

    @Test
    fun `BusinessRuleException returns 422 with UNPROCESSABLE_ENTITY error code`() {
        val resp = get("/test/business-rule")
        assertEquals(422, resp.statusCode(), "should return 422")
        assertEquals("UNPROCESSABLE_ENTITY", jsonField(resp.body(), "error"))
        assertEquals("TRANSACTION_NOT_CHARGED", jsonField(resp.body(), "code"))
        assertEquals("交易必须为 CHARGED 状态", jsonField(resp.body(), "message"))
    }

    @Test
    fun `NotFoundException returns 404 with resource info`() {
        val resp = get("/test/not-found")
        assertEquals(404, resp.statusCode(), "should return 404")
        assertEquals("NOT_FOUND", jsonField(resp.body(), "error"))
        assertEquals("交易 不存在: tx-999", jsonField(resp.body(), "message"))
    }

    @Test
    fun `RuntimeException returns 500 without stack trace`() {
        val resp = get("/test/unexpected")
        assertEquals(500, resp.statusCode(), "should return 500")
        val body = resp.body()
        assertEquals("INTERNAL_ERROR", jsonField(body, "error"))

        // Response body must not leak stack trace / internal info
        assertFalse(body.contains("java.lang"), "500 response should not leak stack info: $body")
        assertFalse(body.contains("RuntimeException"), "500 response should not leak exception class name: $body")
        assertFalse(body.contains("jdbc"), "500 response should not leak internal connection info: $body")
    }

    @Test
    fun `HttpMessageNotReadableException returns generic 400 without Jackson leak`() {
        val resp = http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port/test/not-readable"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{not-valid-json"))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(400, resp.statusCode(), "should return 400")
        val body = resp.body()
        assertEquals("BAD_REQUEST", jsonField(body, "error"))
        assertEquals("请求体格式错误或缺失必填字段", jsonField(body, "message"))
        // Must not echo Jackson / class / field path fragments
        assertFalse(body.contains("JsonParseException"), "must not leak Jackson class: $body")
        assertFalse(body.contains("com.fasterxml"), "must not leak package names: $body")
        assertFalse(body.contains("Unexpected character"), "must not leak parser text: $body")
    }
}

/**
 * Minimal Spring Boot app for exception handler testing.
 */
@SpringBootApplication
class ExceptionTestApp

@RestController
class TestExceptionController {

    @GetMapping("/test/illegal-arg")
    fun illegalArg(): String {
        throw IllegalArgumentException("参数不合法测试")
    }

    @GetMapping("/test/business-rule")
    fun businessRule(): String {
        throw BusinessRuleException("TRANSACTION_NOT_CHARGED", "交易必须为 CHARGED 状态")
    }

    @GetMapping("/test/not-found")
    fun notFound(): String {
        throw NotFoundException("交易", "tx-999")
    }

    @GetMapping("/test/unexpected")
    fun unexpected(): String {
        throw RuntimeException("数据库连接失败：jdbc://internal-host:5432/secret-db")
    }

    /** Echo endpoint used only to trigger HttpMessageNotReadableException via bad JSON POST. */
    @org.springframework.web.bind.annotation.PostMapping("/test/not-readable")
    fun notReadable(@org.springframework.web.bind.annotation.RequestBody body: Map<String, Any>): Map<String, Any> {
        return body
    }
}
