package com.bone.blueprint.infrastructure.security;

import com.bone.blueprint.domain.security.TokenProvider;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import java.util.List;
import org.springframework.stereotype.Component;

/** bone-blueprint JWT 实现。委托给框架层的 {@link JwtTokenService}。 */
@Component
public class JwtTokenProvider implements TokenProvider {

  private final JwtTokenService jwtTokenService;

  public JwtTokenProvider(JwtTokenService jwtTokenService) {
    this.jwtTokenService = jwtTokenService;
  }

  @Override
  public String createToken(String username) {
    // blueprint 示例：使用默认租户和空 scopes
    return jwtTokenService.generateToken(0L, username, 0L, List.of());
  }

  @Override
  public String getUsername(String token) {
    return jwtTokenService.parse(token).map(JwtPrincipal::username).orElse(null);
  }

  @Override
  public boolean validateToken(String token) {
    return jwtTokenService.parse(token).isPresent();
  }
}
