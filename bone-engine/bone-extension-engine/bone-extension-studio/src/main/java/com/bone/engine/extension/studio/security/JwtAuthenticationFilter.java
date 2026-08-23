package com.bone.engine.extension.studio.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 扩展 Studio JWT 认证过滤器。继承框架抽象类，认证成功后绑定 MDC 上下文。 */
@Component
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    super(jwtTokenService, jwtConfig);
  }

  @Override
  protected void onAuthenticated(JwtPrincipal principal, HttpServletRequest request) {
    if (StringUtils.hasText(principal.userId())) {
      MDC.put("userId", principal.userId());
    }
    if (StringUtils.hasText(principal.tenantId())) {
      MDC.put("tenantId", principal.tenantId());
    }
  }

  @Override
  protected void onFinally() {
    MDC.remove("userId");
    MDC.remove("tenantId");
  }
}
