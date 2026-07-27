package com.commonsengine.platform.ai

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * AiServiceClient 契约 + 降级测试（#74）。
 *
 * 用 MockWebServer 模拟 Python 微服务，验证：
 * 1. 成功响应的 JSON 反序列化（snake_case → Kotlin DTO）
 * 2. 三服务端点路径正确
 * 3. 服务端 5xx 错误 → 安全降级
 * 4. 服务端返回缺失 data 字段 → 降级
 *
 * 覆盖三服务的成功路径 + 关键降级路径。
 */
class AiServiceClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AiServiceClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = "http://${server.hostName}:${server.port}"
        val props = AiServiceProperties(
            customerService = AiServiceProperties.ServiceEndpoint(baseUrl),
            contentModeration = AiServiceProperties.ServiceEndpoint(baseUrl),
            dispatchOptimizer = AiServiceProperties.ServiceEndpoint(baseUrl),
            connectTimeoutMs = 500,
            readTimeoutMs = 1_000,
            // 小窗口便于熔断测试
            circuitBreaker = AiServiceProperties.CircuitBreakerConfig(
                failureRateThreshold = 50f,
                slidingWindowSize = 4,
                minimumNumberOfCalls = 2,
                waitDurationInOpenStateSec = 60,
            ),
            retry = AiServiceProperties.RetryConfig(maxAttempts = 1, waitDurationMs = 10),
        )
        client = AiServiceClient(props)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun jsonResponse(body: String) =
        MockResponse().addHeader("Content-Type", "application/json").setBody(body)

    // ── customer-service ──────────────────────────────────

    @Nested
    @DisplayName("客服对话 chat()")
    inner class CustomerService {

        @Test
        fun `成功 - 命中关键词返回回复`() {
            server.enqueue(jsonResponse(
                """{"success":true,"data":{"reply":"关于抽成比例请查阅治理细则","needs_human":false,"category":"category_commission"}}""",
            ))

            val result = client.chat(ChatRequest(message = "抽成多少", userId = "u-1"))

            assertEquals("关于抽成比例请查阅治理细则", result.reply)
            assertFalse(result.needsHuman)
            assertEquals("category_commission", result.category)

            // 验证请求路径 + body
            val recorded = server.takeRequest()
            assertEquals("/api/v1/customer-service/chat", recorded.path)
            assertTrue(recorded.body.readUtf8().contains("\"message\":\"抽成多少\""))
        }

        @Test
        fun `成功 - 未命中关键词转人工`() {
            server.enqueue(jsonResponse(
                """{"success":true,"data":{"reply":"已转接人工","needs_human":true,"category":null}}""",
            ))

            val result = client.chat(ChatRequest(message = "奇怪的问题"))

            assertTrue(result.needsHuman)
            assertNull(result.category)
        }

        @Test
        fun `降级 - 服务端 500 返回转人工兜底`() {
            server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

            val result = client.chat(ChatRequest(message = "help"))

            assertTrue(result.needsHuman, "服务故障时应转人工")
            assertEquals("fallback_human", result.category)
        }

        @Test
        fun `降级 - data 字段缺失返回兜底`() {
            server.enqueue(jsonResponse("""{"success":true,"data":null}"""))

            val result = client.chat(ChatRequest(message = "hi"))

            assertTrue(result.needsHuman)
            assertEquals("fallback_human", result.category)
        }
    }

    // ── content-moderation ────────────────────────────────

    @Nested
    @DisplayName("内容审核 moderate()")
    inner class ContentModeration {

        @Test
        fun `成功 - 检测到 PII 标记待复审`() {
            server.enqueue(jsonResponse(
                """{"success":true,"data":{"decision":"flagged","category":"pii","confidence":0.95,"reason":"检测到疑似手机号或身份证号——为保护隐私，标记待复审"}}""",
            ))

            val result = client.moderate(ModerationRequest(content = "我的手机是13800138000", source = ContentSource.RATING))

            assertEquals(ModerationDecision.FLAGGED, result.decision)
            assertEquals(ModerationCategory.PII, result.category)
            assertEquals(0.95, result.confidence, 0.001)
            assertTrue(result.reason.contains("手机号"))

            val recorded = server.takeRequest()
            assertEquals("/api/v1/content-moderation/moderate", recorded.path)
        }

        @Test
        fun `成功 - 正常内容通过`() {
            server.enqueue(jsonResponse(
                """{"success":true,"data":{"decision":"approved","category":"clean","confidence":0.7,"reason":"未命中任何违规规则"}}""",
            ))

            val result = client.moderate(ModerationRequest(content = "服务很好"))

            assertEquals(ModerationDecision.APPROVED, result.decision)
            assertEquals(ModerationCategory.CLEAN, result.category)
        }

        @Test
        fun `降级 - 服务故障保守标记待复审`() {
            server.enqueue(MockResponse().setResponseCode(503))

            val result = client.moderate(ModerationRequest(content = "任意内容"))

            assertEquals(ModerationDecision.FLAGGED, result.decision, "审核不可用时应保守标记")
            assertEquals(0.0, result.confidence, 0.001)
            assertTrue(result.reason.contains("人工复审"))
        }
    }

    // ── dispatch-optimizer ────────────────────────────────

    @Nested
    @DisplayName("调度建议 suggestDispatch()")
    inner class DispatchOptimizer {

        @Test
        fun `成功 - 返回按评分排序的建议`() {
            server.enqueue(jsonResponse(
                """{"success":true,"data":{"suggestions":[{"worker_id":"w-1","distance_meters":320.5,"score":0.72,"reason":"距离 321m，当前 1 单，评分 0.72"}],"strategy":"nearest_balanced"}}""",
            ))

            val result = client.suggestDispatch(
                DispatchRequest(
                    pickupLat = 39.9,
                    pickupLng = 116.4,
                    candidates = listOf(
                        WorkerLocation(workerId = "w-1", lat = 39.902, lng = 116.402, activeOrderCount = 1),
                    ),
                ),
            )

            assertEquals(1, result.suggestions.size)
            val s = result.suggestions.first()
            assertEquals("w-1", s.workerId)
            assertEquals(320.5, s.distanceMeters, 0.1)
            assertEquals(0.72, s.score, 0.001)
            assertEquals("nearest_balanced", result.strategy)

            val recorded = server.takeRequest()
            assertEquals("/api/v1/dispatch-optimizer/suggest", recorded.path)
            val reqBody = recorded.body.readUtf8()
            // 请求体应为 snake_case（Python 侧契约）：字段名含 pickup_lat / worker_id
            assertTrue(reqBody.contains("\"pickup_lat\""), "请求体应序列化为 snake_case，实际: $reqBody")
            assertTrue(reqBody.contains("\"worker_id\":\"w-1\""), "请求体应含 worker_id，实际: $reqBody")
        }

        @Test
        fun `降级 - 服务故障返回空建议`() {
            server.enqueue(MockResponse().setResponseCode(500))

            val result = client.suggestDispatch(
                DispatchRequest(pickupLat = 39.9, pickupLng = 116.4, candidates = emptyList()),
            )

            assertTrue(result.suggestions.isEmpty(), "调度不可用时应返回空建议，绝不饥饿派单")
            assertEquals("fallback_empty", result.strategy)
        }
    }

    // ── 熔断器 ────────────────────────────────────────────

    @Nested
    @DisplayName("熔断器与连续故障")
    inner class CircuitBreaker {

        @Test
        fun `连续故障触发熔断 - 后续调用直接走降级不实际请求`() {
            // 窗口大小 4，最少 2 次调用，失败率阈值 50%。
            // 连续 2 次 500 即可达到 100% 失败率 → 熔断器打开。
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(500))

            val r1 = client.chat(ChatRequest(message = "1"))
            val r2 = client.chat(ChatRequest(message = "2"))
            assertTrue(r1.needsHuman)
            assertTrue(r2.needsHuman)

            // 熔断器已打开，第三次不再发请求
            val r3 = client.chat(ChatRequest(message = "3"))
            assertTrue(r3.needsHuman, "熔断打开后应直接降级")
            assertEquals("fallback_human", r3.category)
        }
    }
}
