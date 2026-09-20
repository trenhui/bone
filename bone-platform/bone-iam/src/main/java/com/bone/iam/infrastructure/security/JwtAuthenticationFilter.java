package com.bone.iam.infrastructure.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.port.out.TokenBlacklistPort;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** IAM JWT 认证过滤器。继承框架抽象类，增加租户上下文绑定。 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {
  private final TokenBlacklistPort tokenBlacklistPort;

  public JwtAuthenticationFilter(
      JwtTokenService jwtTokenService, JwtConfig jwtConfig, TokenBlacklistPort tokenBlacklistPort) {
    super(jwtTokenService, jwtConfig);
    this.tokenBlacklistPort = tokenBlacklistPort;
  }

  @Override
  protected boolean shouldBlockToken(String rawToken, HttpServletRequest request) {
    return tokenBlacklistPort.isBlacklisted(rawToken);
  }

  @Override
  protected void onAuthenticated(JwtPrincipal principal, HttpServletRequest request) {
    String tenantId = principal.tenantId();
    if (tenantId != null && !tenantId.isBlank()) {
      // 直接以字符串形式设置租户ID（兼容雪花ID与租户编码）
      TenantContext.setTenantId(tenantId);
    }
  }

  @Override
  protected void onFinally() {
    TenantContext.clear();
  }
}
