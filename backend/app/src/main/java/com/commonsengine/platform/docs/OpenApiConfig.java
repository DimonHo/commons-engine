package com.commonsengine.platform.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1 文档元信息配置（#64）。
 *
 * springdoc-openapi 会自动扫描所有 @RestController 生成 API spec，
 * 这里仅补充项目级元信息（标题、描述、许可、联系方式）。
 *
 * 端点：
 * - GET /v3/api-docs        → OpenAPI 3.1 JSON
 * - GET /swagger-ui.html   → Swagger UI 交互式文档
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI commonsEngineOpenAPI() {
        return new OpenAPI().info(
                new Info()
                        .title("公地引擎 Commons Engine API")
                        .version("0.1.0-SNAPSHOT")
                        .description(
                                "公地引擎——开放、社区所有的平台合作社基础设施。" +
                                "提供匹配引擎、分账、评价、纠纷仲裁、治理、身份、调度 7 大模块的 REST API。"
                        )
                        .contact(
                                new Contact()
                                        .name("Commons Engine")
                                        .url("https://github.com/DimonHo/commons-engine")
                        )
                        .license(
                                new License()
                                        .name("AGPL-3.0")
                                        .url("https://www.gnu.org/licenses/agpl-3.0.html")
                        )
        );
    }
}
