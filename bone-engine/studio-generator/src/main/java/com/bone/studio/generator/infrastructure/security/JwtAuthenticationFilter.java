package com.bone.studio.generator.infrastructure.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 代码生成器的 JWT 认证过滤器。
 *
 * <p><b>为什么必须有这个类</b>：本模块的 {@code SecurityConfig} 原先只启用 HTTP Basic （{@code httpBasic()} + {@code
 * anyRequest().authenticated()}），<b>从不校验平台 JWT</b>。而网关与前端统一走 {@code Authorization: Bearer
 * <JWT>}，于是真实联调时生成器全部端点恒 401 （{@code AUTH_UNAUTHORIZED: 未认证或缺少访问凭据}）——HTTP Basic 分支只对携带 Basic
 * 凭据的契约测试有效， 生产流量永远走不到。
 *
 * <p><b>修法</b>：与 {@code bone-iam} / {@code bone-system} / {@code bone-blueprint} 等 8 个模块同范式—— 继承框架
 * {@link AbstractJwtAuthenticationFilter}，用共享密钥 {@code bone.iam.jwt.secret-key} 本地离线验签。 HTTP Basic
 * 分支<b>保留</b>：{@code GeneratorSecurityContractTest} 用 {@code withBasicAuth()} 做真实 HTTP 契约测试，
 * 移除会让既有测试失去认证入口。
 *
 * <p><b>不调用 IAM</b>：IAM 只负责签发 token，各模块本地验签，避免把 IAM 变成所有服务的可用性单点。
 *
 * <p><b>身份写入 MDC</b>：认证成功后写 {@code userId} / {@code tenantId}，供业务日志与 Access Log 关联。 必须在此处写：安全链结束后
 * {@code SecurityContextHolder} 会被清空，外层过滤器已读不到身份。
 */
@Component
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    super(jwtTokenService, jwtConfig);
  }

  @Override
  protected void onAuthenticated(JwtPrincipal principal, HttpServletRequest request) {
    putIfPresent("userId", principal.userId());
    // 租户取 token 内已签名 claim；框架已把 X-Tenant-Id 归一化为同一值，二者等价
    String tenantId =
        principal.tenantId() != null ? principal.tenantId() : request.getHeader("X-Tenant-Id");
    putIfPresent("tenantId", tenantId);
  }

  /** 值为空时不写入，避免 MDC 出现 {@code userId=null} 这类噪声。 */
  private static void putIfPresent(String key, String value) {
    if (value != null && !value.isBlank()) {
      MDC.put(key, value);
    }
  }
}
