package com.bone.integration.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
  @Bean
  public OpenAPI integrationOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Integration Management API")
                .description("集成管理模块API文档")
                .version("1.0"));
  }
}
