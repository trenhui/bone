package com.bone.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;

/** JwtTokenService 签发与解析测试 */
class JwtTokenServiceTest {

  private static final String SECRET = "test-secret-key-32-bytes-minimum-length!!";
  private JwtTokenService jwtTokenService;

  @BeforeEach
  void setUp() {
    JwtConfig config = new JwtConfig(mock(ConfigurableEnvironment.class));
    config.setSecretKey(SECRET);
    config.setExpirationMs(60_000L);
    jwtTokenService = new JwtTokenService(config);
  }

  @Test
  void generateAndParse_roundTrip() {
    String token =
        jwtTokenService.generateToken(42L, "alice", 7L, java.util.List.of("read", "write"));
    assertThat(token).isNotBlank();

    JwtPrincipal principal = jwtTokenService.parse(token).orElseThrow();
    assertThat(principal.userId()).isEqualTo("42");
    assertThat(principal.username()).isEqualTo("alice");
    assertThat(principal.tenantId()).isEqualTo("7");
    assertThat(principal.scopes()).containsExactly("read", "write");
  }

  @Test
  void parse_acceptsBearerPrefix() {
    String token = jwtTokenService.generateToken(1L, "bob", null, null);
    JwtPrincipal principal = jwtTokenService.parse("Bearer " + token).orElseThrow();
    assertThat(principal.username()).isEqualTo("bob");
  }

  @Test
  void parse_nullTenantId_defaultsToZero() {
    String token = jwtTokenService.generateToken(1L, "bob", null, null);
    JwtPrincipal principal = jwtTokenService.parse(token).orElseThrow();
    // 生成时 tenantId 为空会写入 "0"
    assertThat(principal.tenantId()).isEqualTo("0");
    // scopes 为 null 时生成空列表
    assertThat(principal.scopes()).isEmpty();
  }

  @Test
  void parse_invalidToken_returnsEmpty() {
    assertThat(jwtTokenService.parse("not-a-jwt")).isEmpty();
    assertThat(jwtTokenService.parse("")).isEmpty();
    assertThat(jwtTokenService.parse(null)).isEmpty();
    assertThat(jwtTokenService.parse("Bearer ")).isEmpty();
  }

  @Test
  void parse_tamperedToken_returnsEmpty() {
    String token = jwtTokenService.generateToken(1L, "alice", 1L, null);
    String tampered = token.substring(0, token.length() - 4) + "AAAA";
    assertThat(jwtTokenService.parse(tampered)).isEmpty();
  }

  @Test
  void parse_expiredToken_returnsEmpty() {
    // 过期时间设为 -1s：签发的 token 立即过期
    JwtConfig config = new JwtConfig(mock(ConfigurableEnvironment.class));
    config.setSecretKey(SECRET);
    config.setExpirationMs(-1000L);
    JwtTokenService shortLived = new JwtTokenService(config);

    String token = shortLived.generateToken(1L, "alice", 1L, null);
    assertThat(shortLived.parse(token)).isEmpty();
  }

  @Test
  void stripBearerToken_removesPrefix() {
    assertThat(jwtTokenService.stripBearerToken("Bearer abc.def.ghi")).isEqualTo("abc.def.ghi");
    assertThat(jwtTokenService.stripBearerToken("abc.def.ghi")).isEqualTo("abc.def.ghi");
    assertThat(jwtTokenService.stripBearerToken(null)).isEmpty();
  }

  @Test
  void parse_tokenWithDifferentKey_returnsEmpty() {
    JwtConfig other = new JwtConfig(mock(ConfigurableEnvironment.class));
    other.setSecretKey("another-secret-key-32-bytes-minimum-length!");
    JwtTokenService otherService = new JwtTokenService(other);

    String token = jwtTokenService.generateToken(1L, "alice", 1L, null);
    assertThat(otherService.parse(token)).isEmpty();
  }
}
