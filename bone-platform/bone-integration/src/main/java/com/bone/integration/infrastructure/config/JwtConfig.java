package com.bone.integration.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bone.iam.jwt")
@Data
public class JwtConfig {
  private String secretKey = "change-me-change-me-change-me-change-me-32bytes";
  private String tokenPrefix = "Bearer ";
  private String headerName = "Authorization";
}
