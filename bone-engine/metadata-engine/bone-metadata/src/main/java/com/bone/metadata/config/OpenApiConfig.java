package com.bone.metadata.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI 3 配置
 * - 定义全局 JWT Bearer 认证
 * - 定义 API Key 认证方案
 * - 设置文档元信息（标题、版本、描述）
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("元数据服务 API 文档")
                        .version("1.0.0")
                        .description("扩展字段元数据管理模块 - RESTful 接口")
                        .contact(new Contact()
                                .name("技术支持")
                                .email("support@bone.com")
                                .url("https://www.bone.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components()
                        // JWT Bearer 认证模式
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")
                                .description("Bearer 模式，Header: Authorization: Bearer {token}"))
                        // API Key 认证模式
                        .addSecuritySchemes("APIKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("在 Header 中传递 API Key"))
                );
    }
}
