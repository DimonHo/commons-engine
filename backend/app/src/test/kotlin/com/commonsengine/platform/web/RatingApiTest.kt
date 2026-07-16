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
    fun `submit rating persists and returns rating data`() {
        val body = """
            {"transactionId":"tx-1","raterId":"consumer-1","rateeId":"worker-1","direction":"CONSUMER_TO_WORKER","score":5,"tags":["PUNCTUAL","SAFE_DRIVING","POLITE"],"comment":"服务很好"}
        """.trimIndent()
        val resp = post("/api/v1/rating/submit", body)
        assertEquals(200, resp.statusCode(), "submit 应返回 200，实际: ${resp.statusCode()} body=${resp.body()}")

        val json = objectMapper.readTree(resp.body())
        assertEquals("tx-1", json["transactionId"].asText())
        assertEquals("CONSUMER_TO_WORKER", json["direction"].asText())
        assertEquals(5, json["score"].asInt())
        assertTrue(json["tags"].size() == 3)
        assertTrue(json["id"].asText().isNotBlank())
    }

    @Test
    fun `find received ratings returns ratings for a member`() {
        // Submit a rating
        post("/api/v1/rating/submit",
            """{"transactionId":"tx-2","raterId":"c2","rateeId":"w2","direction":"CONSUMER_TO_WORKER","score":4,"tags":["POLITE"]}""")

        val resp = get("/api/v1/rating/received/w2")
        assertEquals(200, resp.statusCode())
        val ratings = objectMapper.readTree(resp.body())
        assertTrue(ratings.size() >= 1, "应至少有 1 条收到的评价")
        assertTrue(ratings.any { it["rateeId"].asText() == "w2" })
    }

    @Test
    fun `find given ratings returns ratings by a member`() {
        post("/api/v1/rating/submit",
            """{"transactionId":"tx-3","raterId":"c3","rateeId":"w3","direction":"CONSUMER_TO_WORKER","score":3,"tags":[]}""")

        val resp = get("/api/v1/rating/given/c3")
        assertEquals(200, resp.statusCode())
        val ratings = objectMapper.readTree(resp.body())
        assertTrue(ratings.size() >= 1)
        assertTrue(ratings.any { it["raterId"].asText() == "c3" })
    }

    @Test
    fun `find by transaction returns bidirectional ratings`() {
        // Consumer → Worker
        post("/api/v1/rating/submit",
            """{"transactionId":"tx-bi","raterId":"c-bi","rateeId":"w-bi","direction":"CONSUMER_TO_WORKER","score":5,"tags":["PUNCTUAL"]}""")
        // Worker → Consumer
        post("/api/v1/rating/submit",
            """{"transactionId":"tx-bi","raterId":"w-bi","rateeId":"c-bi","direction":"WORKER_TO_CONSUMER","score":4,"tags":["POLITE"]}""")

        val resp = get("/api/v1/rating/transaction/tx-bi")
        assertEquals(200, resp.statusCode())
        val ratings = objectMapper.readTree(resp.body())
        assertEquals(2, ratings.size(), "一笔交易应有双向评价")
    }

    @Test
    fun `get credit profile aggregates received ratings`() {
        post("/api/v1/rating/submit",
            """{"transactionId":"tx-p1","raterId":"c-p1","rateeId":"w-profile","direction":"CONSUMER_TO_WORKER","score":5,"tags":["PUNCTUAL","SAFE_DRIVING"]}""")
        post("/api/v1/rating/submit",
            """{"transactionId":"tx-p2","raterId":"c-p2","rateeId":"w-profile","direction":"CONSUMER_TO_WORKER","score":3,"tags":["POLITE"]}""")

        val resp = get("/api/v1/rating/profile/w-profile")
        assertEquals(200, resp.statusCode())
        val profile = objectMapper.readTree(resp.body())
        assertEquals("w-profile", profile["memberId"].asText())
        assertTrue(profile["totalRatings"].asInt() >= 2)
        val avg = profile["averageScore"].asDouble()
        assertTrue(avg >= 3.0 && avg <= 5.0, "平均分应在 3-5 之间，实际: $avg")
    }

    @Test
    fun `export profile returns text with credit history`() {
        post("/api/v1/rating/submit",
            """{"transactionId":"tx-e1","raterId":"c-e1","rateeId":"w-export","direction":"CONSUMER_TO_WORKER","score":5,"tags":["POLITE"],"comment":"好评"}""")

        val resp = get("/api/v1/rating/export/w-export")
        assertEquals(200, resp.statusCode())
        assertTrue(resp.body().contains("信用记录导出"), "导出应包含标题")
        assertTrue(resp.body().contains("w-export"), "导出应包含成员 ID")
        assertTrue(resp.body().contains("可携带"), "导出应包含数据携带声明")
    }
}
