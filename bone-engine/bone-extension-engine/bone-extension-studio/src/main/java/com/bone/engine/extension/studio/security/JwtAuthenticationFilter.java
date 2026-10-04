package com.bone.engine.extension.studio.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 扩展 Studio JWT 认证过滤器。继承框架抽象类，认证成功后绑定 MDC 上下文。 */
/**
 * Studio 端 JWT 认证过滤器。
 *
 * <p>{@code @Order} 为必需：Spring Security 6 会对参与 SecurityFilterChain 的 Filter bean 校验是否已注册
 * order，未注册时直接抛 “The Filter class ... does not have a registered order” 导致上下文启动失败（本模块 以 web
 * 环境启动即命中）。取值大于 ReporterTokenFilter，与链内先后一致。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
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
