package com.bone.engine.extension.studio.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StudioOpenApiConfiguration {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI extensionStudioOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bone Extension Studio API")
                        .description(
                                "扩展引擎管理台 /api/v1/extension；契约见 doc/architecture/openapi/extension-v1.yaml")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("IAM 登录 Token（claim scopes: extension:points:read 等）")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
