package com.commonsengine.platform.exception

import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * GlobalExceptionHandler 集成测试（#63）
 *
 * 验证各类异常场景的 HTTP 状态码和响应体结构。
 */
@SpringBootTest(classes = [ExceptionTestApp::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Test
    fun `IllegalArgumentException returns 400 with BAD_REQUEST error code`(
        mvc: MockMvc,
    ) {
        mvc.perform(get("/test/illegal-arg"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("参数不合法测试"))
    }

    @Test
    fun `BusinessRuleException returns 422 with UNPROCESSABLE_ENTITY error code`(
        mvc: MockMvc,
    ) {
        mvc.perform(get("/test/business-rule"))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.error").value("UNPROCESSABLE_ENTITY"))
            .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_CHARGED"))
            .andExpect(jsonPath("$.message").value("交易必须为 CHARGED 状态"))
    }

    @Test
    fun `NotFoundException returns 404 with resource info`(
        mvc: MockMvc,
    ) {
        mvc.perform(get("/test/not-found"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("交易 不存在: tx-999"))
    }

    @Test
    fun `RuntimeException returns 500 without stack trace`(
        mvc: MockMvc,
    ) {
        val result = mvc.perform(get("/test/unexpected"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
            .andReturn()

        val body = result.response.contentAsString
        // 不泄漏堆栈——响应体不应包含 Java 包名或异常类名
        assert(!body.contains("java.lang")) { "500 响应不应泄漏堆栈信息: $body" }
        assert(!body.contains("RuntimeException")) { "500 响应不应泄漏异常类名: $body" }
        assert(!body.contains("jdbc")) { "500 响应不应泄漏内部连接信息: $body" }
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
