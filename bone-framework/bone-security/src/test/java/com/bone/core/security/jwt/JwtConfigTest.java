package com.bone.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/** JwtConfig 配置校验测试：密钥长度与生产环境 fail-fast */
class JwtConfigTest {

  private ConfigurableEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = mock(ConfigurableEnvironment.class);
    when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
  }

  @Test
  void defaultSecret_passesInNonProd() {
    JwtConfig config = new JwtConfig(environment);
    config.validate(); // 默认密钥长度 >= 32 且非 prod，应通过（仅告警）
  }

  @Test
  void blankSecret_rejected() {
    JwtConfig config = new JwtConfig(environment);
    config.setSecretKey("");
    assertThatThrownBy(config::validate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shortSecret_rejected() {
    JwtConfig config = new JwtConfig(environment);
    config.setSecretKey("too-short");
    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(">= 32");
  }

  @Test
  void defaultSecretInProd_rejected() {
    when(environment.acceptsProfiles(Profiles.of("prod"))).thenReturn(true);
    JwtConfig config = new JwtConfig(environment);
    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("生产环境禁止使用默认 JWT 密钥");
  }

  @Test
  void customSecretInProd_passes() {
    when(environment.acceptsProfiles(Profiles.of("prod"))).thenReturn(true);
    JwtConfig config = new JwtConfig(environment);
    config.setSecretKey("custom-prod-secret-32-bytes-minimum-length!!");
    config.validate(); // 自定义密钥 + prod：应通过
    assertThat(config.getSecretKey()).startsWith("custom-prod-secret");
  }

  @Test
  void defaults_areSane() {
    JwtConfig config = new JwtConfig(environment);
    assertThat(config.getExpirationMs()).isEqualTo(2 * 60 * 60 * 1000L);
    assertThat(config.getRefreshExpirationMs()).isEqualTo(7 * 24 * 60 * 60 * 1000L);
    assertThat(config.getTokenPrefix()).isEqualTo("Bearer ");
    assertThat(config.getHeaderName()).isEqualTo("Authorization");
  }
}
