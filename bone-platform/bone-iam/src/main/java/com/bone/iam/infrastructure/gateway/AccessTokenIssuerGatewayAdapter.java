package com.bone.iam.infrastructure.gateway;

import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.model.permission.DefaultPermissionCodes;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenIssuerGatewayAdapter implements AccessTokenIssuer {
  private final JwtTokenService delegate;
  private final JwtConfig jwtConfig;

  public AccessTokenIssuerGatewayAdapter(JwtTokenService delegate, JwtConfig jwtConfig) {
    this.delegate = delegate;
    this.jwtConfig = jwtConfig;
  }

  /** HS256/RS256 双模签发：开启 {@code rsaIssuanceEnabled} 后改发 RS256（ADR-0005 双轨过渡）。 */
  private String issue(Long accountId, String username, Long tenantId, List<String> scopes) {
    if (jwtConfig.isRsaIssuanceEnabled()) {
      return delegate.generateRsaToken(accountId, username, tenantId, scopes);
    }
    return delegate.generateToken(accountId, username, tenantId, scopes);
  }

  public String generateToken(Long accountId, String username) {
    return issue(accountId, username, 0L, DefaultPermissionCodes.adminFallback());
  }

  @Override
  public String issueAccessToken(
      Long accountId, String username, Long tenantId, List<String> scopes) {
    return issue(accountId, username, tenantId, scopes);
  }

  public String generateToken(Long accountId, String username, Long tenantId, List<String> scopes) {
    return issue(accountId, username, tenantId, scopes);
  }

  public java.util.Optional<JwtPrincipal> parse(String rawToken) {
    return delegate.parse(rawToken);
  }

  public String stripBearerToken(String token) {
    return delegate.stripBearerToken(token);
  }
}
