package com.bone.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 白名单默认值订正（G-4）：Java 默认必须与 IAM 真实登录端点一致。 */
class GatewayJwtPropertiesTest {

  @Test
  @DisplayName("默认白名单登录项是 /api/v1/iam/login（无 /auth 段），且包含健康检查")
  void defaultWhitelistPointsToRealIamLoginEndpoint() {
    GatewayJwtProperties properties = new GatewayJwtProperties();
    assertThat(properties.getWhitelist())
        .contains("/api/v1/iam/login", "/actuator/health", "/favicon.ico")
        .doesNotContain("/api/v1/iam/auth/login");
  }
}
