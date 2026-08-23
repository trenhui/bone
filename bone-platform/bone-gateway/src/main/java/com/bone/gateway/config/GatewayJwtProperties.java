package com.bone.gateway.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 网关 JWT 鉴权配置。密钥须与下游签发服务共享（BONE_JWT_SECRET 注入）。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bone.gateway.jwt")
public class GatewayJwtProperties {

  /** HS256 对称密钥，须与 bone-iam 等签发模块一致（默认仅限开发，生产用 BONE_JWT_SECRET）。 */
  private String secretKey = "change-me-change-me-change-me-change-me-32bytes";

  /** Bearer 前缀。 */
  private String tokenPrefix = "Bearer ";

  /** Authorization 头名。 */
  private String headerName = "Authorization";

  /** 免校验白名单路径前缀。 */
  private List<String> whitelist =
      new ArrayList<>(List.of("/api/v1/iam/auth/login", "/actuator/", "/favicon.ico"));
}
