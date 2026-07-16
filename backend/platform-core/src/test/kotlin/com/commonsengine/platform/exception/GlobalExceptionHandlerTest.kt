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
import tools.jackson.databind.ObjectMapper

/**
 * GlobalExceptionHandler 集成测试（#63）
 *
 * 验证各类异常场景的 HTTP 状态码和响应体结构。
 *
 * 采用与 [com.commonsengine.platform.web.MatchingHttpApiTest] 一致的模式：
 * `@SpringBootTest(RANDOM_PORT)` + JDK `HttpClient`，走完整 HTTP 路径。
 * 不依赖 MockMvc（platform-core 是库模块，无需 spring-boot-test-autoconfigure web.servlet）。
 */
@SpringBootTest(
    classes = [ExceptionTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @LocalServerPort
    private var port: Int = 0

    @Suppress("unused")  // Spring 注入
    private lateinit var objectMapper: ObjectMapper

    private val http: HttpClient = HttpClient.newHttpClient()

    private fun get(path: String): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:$port$path"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `IllegalArgumentException returns 400 with BAD_REQUEST error code`() {
        val resp = get("/test/illegal-arg")
        assertEquals(400, resp.statusCode(), "应返回 400")
        val tree = objectMapper.readTree(resp.body())
        assertEquals("BAD_REQUEST", tree["error"].asText())
        assertEquals("参数不合法测试", tree["message"].asText())
    }

    @Test
    fun `BusinessRuleException returns 422 with UNPROCESSABLE_ENTITY error code`() {
        val resp = get("/test/business-rule")
        assertEquals(422, resp.statusCode(), "应返回 422")
        val tree = objectMapper.readTree(resp.body())
        assertEquals("UNPROCESSABLE_ENTITY", tree["error"].asText())
        assertEquals("TRANSACTION_NOT_CHARGED", tree["code"].asText())
        assertEquals("交易必须为 CHARGED 状态", tree["message"].asText())
    }

    @Test
    fun `NotFoundException returns 404 with resource info`() {
        val resp = get("/test/not-found")
        assertEquals(404, resp.statusCode(), "应返回 404")
        val tree = objectMapper.readTree(resp.body())
        assertEquals("NOT_FOUND", tree["error"].asText())
        assertEquals("交易 不存在: tx-999", tree["message"].asText())
    }

    @Test
    fun `RuntimeException returns 500 without stack trace`() {
        val resp = get("/test/unexpected")
        assertEquals(500, resp.statusCode(), "应返回 500")
        val body = resp.body()
        val tree = objectMapper.readTree(body)
        assertEquals("INTERNAL_ERROR", tree["error"].asText())

        // 不泄漏堆栈——响应体不应包含 Java 包名或异常类名
        assertFalse(body.contains("java.lang"), "500 响应不应泄漏堆栈信息: $body")
        assertFalse(body.contains("RuntimeException"), "500 响应不应泄漏异常类名: $body")
        assertFalse(body.contains("jdbc"), "500 响应不应泄漏内部连接信息: $body")
    }
}

/**
 * 测试专用 Spring Boot 应用 + 测试 Controller
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
}
