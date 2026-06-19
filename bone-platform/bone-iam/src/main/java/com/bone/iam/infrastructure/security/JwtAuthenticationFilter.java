package com.bone.iam.infrastructure.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import com.bone.core.tenant.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * IAM JWT 认证过滤器。继承框架抽象类，增加租户上下文绑定。
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {
  private final TokenBlacklistService tokenBlacklistService;

  public JwtAuthenticationFilter(
      JwtTokenService jwtTokenService,
      JwtConfig jwtConfig,
      TokenBlacklistService tokenBlacklistService) {
    super(jwtTokenService, jwtConfig);
    this.tokenBlacklistService = tokenBlacklistService;
  }

  @Override
  protected boolean shouldBlockToken(String rawToken, HttpServletRequest request) {
    return tokenBlacklistService.isBlacklisted(rawToken);
  }

  @Override
  protected void onAuthenticated(JwtPrincipal principal, HttpServletRequest request) {
    String tenantId = principal.tenantId();
    if (tenantId != null && !tenantId.isBlank()) {
      try {
        TenantContext.setTenantId(Long.parseLong(tenantId));
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
  }

  @Override
  protected void onFinally() {
    TenantContext.clear();
  }
}
