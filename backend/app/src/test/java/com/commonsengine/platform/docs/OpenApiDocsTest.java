package com.commonsengine.platform.docs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAPI 文档生成验证测试（#64）。
 *
 * 验证 springdoc-openapi 正确生成了 API spec 并覆盖所有 8 个业务模块。
 * 验收标准对应 Issue #64：
 * - [x] 所有 8 个模块的 Controller 端点均出现在 OpenAPI spec 中
 * - [x] API 文档可通过 URL 访问（/v3/api-docs）
 * - [x] Swagger UI 可访问（/swagger-ui.html）
 *
 * — Commons Engine Chief Engineer Bot（AI）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocsTest {

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

    @Test
    void apiDocsEndpointReturnsOpenApi3Spec() throws Exception {
        HttpResponse<String> resp = get("/v3/api-docs");
        assertEquals(200, resp.statusCode(), "/v3/api-docs 应返回 200，实际: " + resp.statusCode());
        assertTrue(resp.body().contains("\"openapi\""), "应包含 openapi 字段: " + resp.body().substring(0, Math.min(200, resp.body().length())));
    }

    @Test
    void apiDocsContainsProjectTitleAndVersion() throws Exception {
        HttpResponse<String> resp = get("/v3/api-docs");
        assertEquals(200, resp.statusCode());
        String body = resp.body();
        assertTrue(body.contains("公地引擎"), "spec 应包含项目标题「公地引擎」: " + body.substring(0, Math.min(300, body.length())));
        assertTrue(body.contains("0.1.0-SNAPSHOT"), "spec 应包含版本号: " + body.substring(0, Math.min(300, body.length())));
    }

    @Test
    void apiDocsCoversAll8ModulesEndpoints() throws Exception {
        HttpResponse<String> resp = get("/v3/api-docs");
        assertEquals(200, resp.statusCode());
        String body = resp.body();
        // 8 个模块的 API 路径前缀——每个都必须出现在 spec 的 paths 中
        String[][] expectedPaths = {
                {"/api/v1/members", "identity"},
                {"/api/v1/matching", "matching-engine"},
                {"/api/v1/payment", "payment"},
                {"/api/v1/rating", "rating"},
                {"/api/v1/dispatch", "dispatch"},
                {"/api/v1/governance", "governance"},
                {"/api/v1/dispute", "dispute"},
                {"/api/v1/platform", "platform-health"},
        };
        for (String[] entry : expectedPaths) {
            String pathPrefix = entry[0];
            String module = entry[1];
            assertTrue(
                    body.contains(pathPrefix),
                    "spec 应覆盖 " + module + " 模块（路径前缀 " + pathPrefix + " 缺失）"
            );
        }
    }

    @Test
    void apiDocsContainsNonTrivialNumberOfPaths() throws Exception {
        HttpResponse<String> resp = get("/v3/api-docs");
        assertEquals(200, resp.statusCode());
        String body = resp.body();
        // 约 30 个端点，paths 对象应包含大量路径
        // 统计 "/api/v1/" 出现次数作为粗略度量
        int apiPathCount = countOccurrences(body, "/api/v1/");
        assertTrue(
                apiPathCount >= 20,
                "spec 应包含至少 20 处 /api/v1/ 路径引用（约 30 个端点），实际: " + apiPathCount
        );
    }

    @Test
    void swaggerUiIsAccessible() throws Exception {
        HttpResponse<String> resp = get("/swagger-ui.html");
        // swagger-ui.html 通常 302 重定向到 /swagger-ui/index.html
        assertTrue(
                resp.statusCode() == 200 || resp.statusCode() == 302,
                "/swagger-ui.html 应返回 200 或 302 重定向，实际: " + resp.statusCode()
        );
    }

    @Test
    void apiDocsIncludesLicenseInfo() throws Exception {
        HttpResponse<String> resp = get("/v3/api-docs");
        assertEquals(200, resp.statusCode());
        String body = resp.body();
        assertTrue(body.contains("AGPL"), "spec 应包含许可证信息（AGPL）: " + body.substring(0, Math.min(500, body.length())));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
