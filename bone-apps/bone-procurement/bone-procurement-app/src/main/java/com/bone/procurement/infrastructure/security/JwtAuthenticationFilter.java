package com.bone.procurement.infrastructure.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** 采购应用 JWT 认证过滤器：把 IAM 签发的 Bearer token 解析为安全上下文主体。 */
@Component
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    super(jwtTokenService, jwtConfig);
  }

  @Override
  protected void onAuthenticated(JwtPrincipal principal, HttpServletRequest request) {
    if (principal.userId() != null && !principal.userId().isBlank()) {
      MDC.put("userId", principal.userId());
    }
  }
}
