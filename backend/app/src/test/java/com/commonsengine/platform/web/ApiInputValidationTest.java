package com.commonsengine.platform.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enum 解析一致性 + Bean Validation 集成测试（#67 + #68）。
 *
 * 验证所有 Controller 对非法输入的一致行为：
 * - 非法枚举值 → 400 Bad Request（#67，通过 Enums.parse → IllegalArgumentException → GlobalExceptionHandler）
 * - 缺失/空必填字段 → 400 Bad Request（#68，通过 @Valid + JSR-380 → MethodArgumentNotValidException）
 *
 * — Commons Engine Chief Engineer Bot（AI）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiInputValidationTest {

    @LocalServerPort
    private int port;

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

    // ── #67: Enum 解析一致性 ──────────────────────────

    @Test
    void rating_invalidDirectionEnumReturns400Not500() throws Exception {
        String body = "{\"transactionId\":\"tx1\",\"raterId\":\"a\",\"rateeId\":\"b\",\"direction\":\"INVALID_DIR\",\"score\":5}";
        HttpResponse<String> resp = post("/api/v1/rating/submit", body);
        assertEquals(400, resp.statusCode(), "非法 direction 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("BAD_REQUEST"), "应包含 BAD_REQUEST: " + resp.body());
        assertTrue(resp.body().contains("RatingDirection"), "错误信息应指明枚举类型: " + resp.body());
    }

    @Test
    void rating_invalidTagEnumReturns400NotSilentDrop() throws Exception {
        // 此前行为：无效 tag 被 mapNotNull 静默丢弃
        String body = "{\"transactionId\":\"tx2\",\"raterId\":\"a\",\"rateeId\":\"b\",\"direction\":\"WORKER_TO_CONSUMER\",\"score\":5,\"tags\":[\"FAKE_TAG\"]}";
        HttpResponse<String> resp = post("/api/v1/rating/submit", body);
        assertEquals(400, resp.statusCode(), "非法 tag 应返回 400（不再静默丢弃），实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("RatingTag"), "应指明 RatingTag 枚举: " + resp.body());
    }

    @Test
    void dispatch_invalidServiceTypeEnumReturns400Not500() throws Exception {
        String body = "{\"workerId\":\"w1\",\"serviceType\":\"NOT_A_SERVICE\",\"pickups\":[{\"lat\":1.0,\"lng\":2.0}],\"dropoffs\":[{\"lat\":3.0,\"lng\":4.0}]}";
        HttpResponse<String> resp = post("/api/v1/dispatch/tasks", body);
        assertEquals(400, resp.statusCode(), "非法 serviceType 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("ServiceType"), "应指明 ServiceType: " + resp.body());
    }

    @Test
    void dispatch_invalidPreferenceServiceTypeReturns400NotSilentDrop() throws Exception {
        // 此前行为：无效偏好类型被 mapNotNull 静默丢弃
        String body = "{\"preferredServiceTypes\":[\"BOGUS_TYPE\"]}";
        HttpResponse<String> resp = post("/api/v1/dispatch/workers/w9/preferences", body);
        assertEquals(400, resp.statusCode(), "非法偏好 serviceType 应返回 400（不再静默丢弃），实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void dispute_invalidTypeEnumReturns400Not500() throws Exception {
        String body = "{\"transactionId\":\"tx3\",\"filedBy\":\"a\",\"filedAgainst\":\"b\",\"type\":\"WRONG_TYPE\",\"description\":\"test\"}";
        HttpResponse<String> resp = post("/api/v1/dispute/file", body);
        assertEquals(400, resp.statusCode(), "非法 dispute type 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("DisputeType"), "应指明 DisputeType: " + resp.body());
    }

    @Test
    void dispute_invalidStatusFilterReturns400Not500() throws Exception {
        HttpResponse<String> resp = get("/api/v1/dispute?status=NO_SUCH_STATUS");
        assertEquals(400, resp.statusCode(), "非法 status 过滤应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("DisputeStatus"), "应指明 DisputeStatus: " + resp.body());
    }

    @Test
    void governance_invalidProposalTypeReturns400NotSilentDefault() throws Exception {
        // 此前行为：无效 type 被 getOrDefault(OTHER) 静默降级
        String body = "{\"title\":\"t\",\"description\":\"d\",\"proposedBy\":\"p\",\"type\":\"MADE_UP_TYPE\"}";
        HttpResponse<String> resp = post("/api/v1/governance/proposals", body);
        assertEquals(400, resp.statusCode(), "非法 proposal type 应返回 400（不再静默降级为 OTHER），实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("ProposalType"), "应指明 ProposalType: " + resp.body());
    }

    @Test
    void matching_invalidServiceTypeReturns400Not500() throws Exception {
        String body = "{\"consumerId\":\"c1\",\"serviceType\":\"NOPE\",\"pickupLat\":1.0,\"pickupLng\":2.0,\"candidates\":[]}";
        HttpResponse<String> resp = post("/api/v1/matching/match", body);
        assertEquals(400, resp.statusCode(), "非法 serviceType 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("ServiceType"), "应指明 ServiceType: " + resp.body());
    }

    @Test
    void identity_invalidMemberRoleReturns400NotSilentDrop() throws Exception {
        // 此前行为：无效 role 被 mapNotNull 静默丢弃
        String body = "{\"name\":\"test\",\"phone\":\"123\",\"roles\":[\"SUPERUSER\"]}";
        HttpResponse<String> resp = post("/api/v1/members/register", body);
        assertEquals(400, resp.statusCode(), "非法 role 应返回 400（不再静默丢弃），实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("MemberRole"), "应指明 MemberRole: " + resp.body());
    }

    // ── #68: Bean Validation（必填字段缺失/空） ──────────

    @Test
    void payment_blankConsumerIdReturns400() throws Exception {
        String body = "{\"consumerId\":\"\",\"workerId\":\"w\",\"amount\":\"10.00\",\"serviceType\":\"RIDE_HAILING\"}";
        HttpResponse<String> resp = post("/api/v1/payment/charge", body);
        assertEquals(400, resp.statusCode(), "空 consumerId 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("BAD_REQUEST"), "应包含 BAD_REQUEST: " + resp.body());
    }

    @Test
    void payment_zeroAmountReturns400() throws Exception {
        String body = "{\"consumerId\":\"c\",\"workerId\":\"w\",\"amount\":\"0\",\"serviceType\":\"RIDE_HAILING\"}";
        HttpResponse<String> resp = post("/api/v1/payment/charge", body);
        assertEquals(400, resp.statusCode(), "零金额应返回 400（@Positive），实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void payment_negativeAmountReturns400() throws Exception {
        String body = "{\"consumerId\":\"c\",\"workerId\":\"w\",\"amount\":\"-5.00\",\"serviceType\":\"RIDE_HAILING\"}";
        HttpResponse<String> resp = post("/api/v1/payment/charge", body);
        assertEquals(400, resp.statusCode(), "负金额应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void payment_missingRequiredFieldReturns400() throws Exception {
        // 缺少 serviceType 字段
        String body = "{\"consumerId\":\"c\",\"workerId\":\"w\",\"amount\":\"10.00\"}";
        HttpResponse<String> resp = post("/api/v1/payment/charge", body);
        assertEquals(400, resp.statusCode(), "缺失必填字段应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void rating_scoreOutOfRangeReturns400() throws Exception {
        String body = "{\"transactionId\":\"tx\",\"raterId\":\"a\",\"rateeId\":\"b\",\"direction\":\"WORKER_TO_CONSUMER\",\"score\":99}";
        HttpResponse<String> resp = post("/api/v1/rating/submit", body);
        assertEquals(400, resp.statusCode(), "score 超范围应返回 400（@Max(5)），实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void rating_blankTransactionIdReturns400() throws Exception {
        String body = "{\"transactionId\":\"\",\"raterId\":\"a\",\"rateeId\":\"b\",\"direction\":\"WORKER_TO_CONSUMER\",\"score\":3}";
        HttpResponse<String> resp = post("/api/v1/rating/submit", body);
        assertEquals(400, resp.statusCode(), "空 transactionId 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void dispatch_emptyPickupsListReturns400() throws Exception {
        String body = "{\"workerId\":\"w\",\"serviceType\":\"RIDE_HAILING\",\"pickups\":[],\"dropoffs\":[{\"lat\":1.0,\"lng\":2.0}]}";
        HttpResponse<String> resp = post("/api/v1/dispatch/tasks", body);
        assertEquals(400, resp.statusCode(), "空 pickups 应返回 400（@NotEmpty），实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void dispute_blankDescriptionReturns400() throws Exception {
        String body = "{\"transactionId\":\"tx\",\"filedBy\":\"a\",\"filedAgainst\":\"b\",\"type\":\"FARE_DISPUTE\",\"description\":\"\"}";
        HttpResponse<String> resp = post("/api/v1/dispute/file", body);
        assertEquals(400, resp.statusCode(), "空 description 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void governance_blankTitleReturns400() throws Exception {
        String body = "{\"title\":\"\",\"description\":\"d\",\"proposedBy\":\"p\",\"type\":\"OTHER\"}";
        HttpResponse<String> resp = post("/api/v1/governance/proposals", body);
        assertEquals(400, resp.statusCode(), "空 title 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void identity_emptyRolesListReturns400() throws Exception {
        String body = "{\"name\":\"test\",\"phone\":\"123\",\"roles\":[]}";
        HttpResponse<String> resp = post("/api/v1/members/register", body);
        assertEquals(400, resp.statusCode(), "空 roles 应返回 400（@NotEmpty），实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void identity_blankNameReturns400() throws Exception {
        String body = "{\"name\":\"\",\"phone\":\"123\",\"roles\":[\"WORKER\"]}";
        HttpResponse<String> resp = post("/api/v1/members/register", body);
        assertEquals(400, resp.statusCode(), "空 name 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
    }

    @Test
    void matching_blankStrategyReturns400() throws Exception {
        String body = "{\"strategy\":\"\"}";
        HttpResponse<String> resp = post("/api/v1/matching/strategy", body);
        assertEquals(400, resp.statusCode(), "空 strategy 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
    }

    // ── #71: HttpMessageNotReadableException 信息泄漏加固 ──

    @Test
    void malformedJsonBodyReturns400WithGenericMessageNoInternalLeak() throws Exception {
        // 故意发送非法 JSON（缺少引号、类型错误等结构性破坏）
        String malformed = "{\"consumerId\": INVALID_NOT_A_STRING, \"amount\": 10.00}";
        HttpResponse<String> resp = post("/api/v1/payment/charge", malformed);
        assertEquals(400, resp.statusCode(), "非法 JSON 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("BAD_REQUEST"), "应包含 BAD_REQUEST: " + resp.body());
        assertTrue(resp.body().contains("请求体格式错误"), "应返回通用文案: " + resp.body());
        // 关键断言：响应体不得泄漏 Jackson 内部类名 / 字段路径 / 序列化细节
        assertFalse(resp.body().contains("JsonParseException"), "响应体不得泄漏 Jackson 异常类名: " + resp.body());
        assertFalse(resp.body().contains("MismatchedInputException"), "响应体不得泄漏 MismatchedInputException: " + resp.body());
        assertFalse(resp.body().contains("UnrecognizedPropertyException"), "响应体不得泄漏 UnrecognizedPropertyException: " + resp.body());
        assertFalse(resp.body().contains("at [Source"), "响应体不得泄漏 Jackson 位置信息: " + resp.body());
        assertFalse(resp.body().contains("line:"), "响应体不得泄漏行号信息: " + resp.body());
    }

    @Test
    void wrongTypeForFieldReturns400GenericMessageNoFieldPathLeak() throws Exception {
        // 类型不匹配：amount 传 boolean，Spring 反序列化失败抛 HttpMessageNotReadableException
        String wrongType = "{\"consumerId\":\"c\",\"workerId\":\"w\",\"amount\":true,\"serviceType\":\"RIDE_HAILING\"}";
        HttpResponse<String> resp = post("/api/v1/payment/charge", wrongType);
        assertEquals(400, resp.statusCode(), "类型错误应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("请求体格式错误"), "应返回通用文案: " + resp.body());
        // 字段路径（如 "amount"）是 Jackson 错误信息的一部分，不应泄漏到响应体
        assertFalse(resp.body().contains("BigDecimal"), "响应体不得泄漏目标 Java 类型名: " + resp.body());
        assertFalse(resp.body().contains("Boolean"), "响应体不得泄漏源类型线索: " + resp.body());
    }

    @Test
    void fieldLevelValidationPathStillWorksWhenBodyIsPresentButBlank() throws Exception {
        // 回归保护：HttpMessageNotReadableException 加固不应影响 @Valid 路径。
        // 当 JSON 反序列化成功但字段违反 Bean Validation（如空白字符串），
        // 仍走 MethodArgumentNotValidException 路径，返回字段级错误。
        // 这与 #68 的 `blank consumerId returns 400` 互补——此处断言响应体不含 Jackson 内部信息。
        String blankConsumerId = "{\"consumerId\":\"\",\"workerId\":\"w\",\"amount\":\"10.00\",\"serviceType\":\"RIDE_HAILING\"}";
        HttpResponse<String> resp = post("/api/v1/payment/charge", blankConsumerId);
        assertEquals(400, resp.statusCode(), "空白 consumerId 应返回 400，实际: " + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body().contains("consumerId"), "应返回字段级提示: " + resp.body());
        // 加固一致性：@Valid 路径也不应泄漏 Jackson 内部异常类名
        assertFalse(resp.body().contains("JsonParseException"), "响应体不得泄漏 Jackson 异常类名: " + resp.body());
        assertFalse(resp.body().contains("MismatchedInputException"), "响应体不得泄漏 MismatchedInputException: " + resp.body());
    }
}
