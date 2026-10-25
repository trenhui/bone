package com.bone.claim.infrastructure.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 理赔模块 JWT 认证过滤器（与平台其它模块同一范式，见 bone-blueprint 同名类）。
 *
 * <p><b>只做一件事</b>：把 {@code Authorization: Bearer} token 解析为 {@link JwtPrincipal} 写入 Spring Security
 * 上下文。token 由 IAM 用共享密钥 {@code bone.iam.jwt.secret-key} 签发，本模块本地离线验参（{@link JwtTokenService}），不回调
 * IAM。租户头 {@code X-Tenant-Id} 已由框架基类归一化为 token 内已签名 claim， {@code bone-web} 的 {@code
 * TenantInterceptor} 据此建立 {@code com.bone.core.tenant.context.TenantContext}。
 *
 * <p><b>身份写入 MDC</b>：理赔提交的幂等快照以 {@code MDC.get("userId")} 作为用户维度键（见 {@code
 * ClaimCaseController#submit}），故认证成功后必须把 userId 写入 MDC——Spring Security 安全链结束即清空
 * SecurityContextHolder，业务层已读不到身份，而 MDC 存活整个请求周期。
 */
@Component
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    super(jwtTokenService, jwtConfig);
  }

  @Override
  protected void onAuthenticated(JwtPrincipal principal, HttpServletRequest request) {
    putIfPresent("userId", principal.userId());
    // 租户取 token 内已签名 claim；该头此时也已被本过滤器归一化为同一值，二者等价
    putIfPresent(
        "tenantId",
        principal.tenantId() != null ? principal.tenantId() : request.getHeader("X-Tenant-Id"));
  }

  /** 值为空时不写入，避免 MDC 出现 "userId=null" 这类噪声。 */
  private static void putIfPresent(String key, String value) {
    if (value != null && !value.isBlank()) {
      MDC.put(key, value);
    }
  }
}
