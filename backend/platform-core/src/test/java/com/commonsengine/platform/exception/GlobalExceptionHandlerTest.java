package com.commonsengine.platform.exception;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * GlobalExceptionHandler integration test.
 *
 * <p>Validates HTTP status codes and response body structure for each
 * exception type via real HTTP (RANDOM_PORT + JDK HttpClient).
 *
 * <p>Adapted from the Kotlin original to match the Java ErrorResponse shape:
 * {@code {code, message, timestamp}}.
 */
@SpringBootTest(
        classes = GlobalExceptionHandlerTest.ExceptionTestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    /** Extract a JSON string field value by key (simple regex, avoids Jackson dependency). */
    private static String jsonField(String body, String key) {
        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher m = regex.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    @Test
    void illegalArgumentReturns400WithBadRequestErrorCode() throws Exception {
        HttpResponse<String> resp = get("/test/illegal-arg");
        assertEquals(400, resp.statusCode(), "should return 400");
        assertEquals("BAD_ARGUMENT", jsonField(resp.body(), "code"));
        assertEquals("参数不合法测试", jsonField(resp.body(), "message"));
    }

    @Test
    void businessRuleExceptionReturns422WithErrorCode() throws Exception {
        HttpResponse<String> resp = get("/test/business-rule");
        assertEquals(422, resp.statusCode(), "should return 422");
        assertEquals("TRANSACTION_NOT_CHARGED", jsonField(resp.body(), "code"));
        assertEquals("交易必须为 CHARGED 状态", jsonField(resp.body(), "message"));
    }

    @Test
    void notFoundExceptionReturns404WithResourceInfo() throws Exception {
        HttpResponse<String> resp = get("/test/not-found");
        assertEquals(404, resp.statusCode(), "should return 404");
        assertEquals("交易_tx-999", jsonField(resp.body(), "code"));
        assertEquals("交易 不存在: tx-999", jsonField(resp.body(), "message"));
    }

    @Test
    void runtimeExceptionReturns500WithoutStackTrace() throws Exception {
        HttpResponse<String> resp = get("/test/unexpected");
        assertEquals(500, resp.statusCode(), "should return 500");
        String body = resp.body();
        assertEquals("INTERNAL_ERROR", jsonField(body, "code"));

        // Response body must not leak stack trace / internal info
        assertFalse(body.contains("java.lang"), "500 response should not leak stack info: " + body);
        assertFalse(body.contains("RuntimeException"), "500 response should not leak exception class name: " + body);
        assertFalse(body.contains("jdbc"), "500 response should not leak internal connection info: " + body);
    }

    /** Minimal Spring Boot app for exception handler testing. */
    @SpringBootApplication
    static class ExceptionTestApp {
    }

    @RestController
    static class TestExceptionController {

        @GetMapping("/test/illegal-arg")
        public String illegalArg() {
            throw new IllegalArgumentException("参数不合法测试");
        }

        @GetMapping("/test/business-rule")
        public String businessRule() {
            throw new BusinessRuleException("TRANSACTION_NOT_CHARGED", "交易必须为 CHARGED 状态");
        }

        @GetMapping("/test/not-found")
        public String notFound() {
            // Java NotFoundException takes (code, message); encode resource+id in code.
            throw new NotFoundException("交易_tx-999", "交易 不存在: tx-999");
        }

        @GetMapping("/test/unexpected")
        public String unexpected() {
            throw new RuntimeException("数据库连接失败：jdbc://internal-host:5432/secret-db");
        }
    }
}
