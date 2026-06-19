package com.bone.iam.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtTokenService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

class JwtTokenServiceTest {

  private JwtTokenService jwtTokenService;

  @BeforeEach
  void setUp() {
    ConfigurableEnvironment env = mock(ConfigurableEnvironment.class);
    when(env.acceptsProfiles(Profiles.of("prod"))).thenReturn(false);
    JwtConfig config = new JwtConfig(env);
    config.setSecretKey("test-secret-key-at-least-32-bytes-long!!");
    config.setExpirationMs(3_600_000L);
    config.setTokenPrefix("Bearer ");
    config.setHeaderName("Authorization");
    jwtTokenService = new JwtTokenService(config);
  }

  @Test
  void generateTokenEmbedsScopesAndTenantId() {
    List<String> scopes = List.of("metadata:read", "extension:plugins:deploy");
    String token = jwtTokenService.generateToken(1L, "admin", 0L, scopes);

    var principal = jwtTokenService.parse("Bearer " + token);
    assertTrue(principal.isPresent());
    assertEquals("admin", principal.get().username());
    assertEquals("1", principal.get().userId());
    assertEquals("0", principal.get().tenantId());
    assertEquals(scopes, principal.get().scopes());
  }
}
