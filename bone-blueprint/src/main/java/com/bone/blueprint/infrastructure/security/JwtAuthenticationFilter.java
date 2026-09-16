package com.bone.blueprint.infrastructure.security;

import com.bone.blueprint.infrastructure.observability.BoneRequestContextFilter;
import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.security.jwt.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 订单 / 支付样例的 JWT 认证过滤器。
 *
 * <p><b>与平台同范式</b>：继承框架 {@link AbstractJwtAuthenticationFilter}，只做一件事——把 {@code Authorization:
 * Bearer} token 解析成 {@code JwtPrincipal} 写入 Spring Security 上下文。其余 7 个模块 （{@code bone-system} /
 * {@code bone-integration} / {@code bone-metadata-server} / …）的写法与此完全一致。
 *
 * <p><b>为什么不自己做租户绑定</b>：框架已经把请求头 {@code X-Tenant-Id} 归一化为 token 内<strong>已签名</strong>的 {@code
 * tenantId} claim（见 {@link AbstractJwtAuthenticationFilter} 类注释），所以本类不必再写租户绑定或 claim↔header
 * 交叉校验——{@code bone-web} 的 {@code TenantInterceptor}（本模块在 {@code WebMvcConfiguration} 注册）
 * 原样读该头即可，读到的必是真值。在这里再补一层只会让同一规则出现两处实现、各自演化。
 *
 * <p><b>与 IAM 的关系：不调用 IAM</b>。IAM 负责签发 token（{@code AuthController}）与账号管理，各模块只用共享密钥 （{@code
 * bone.iam.jwt.secret-key}）做<strong>本地离线验签</strong>。若改成每次请求回调 IAM，就把 IAM 变成了所有
 * 服务的可用性单点，也会给每次调用加上一跳额外延迟。
 *
 * <p><b>身份写入 MDC</b>：认证成功后把 {@code userId} / {@code tenantId} 写入 MDC（日志规范 §3），供业务日志与 Access Log
 * 关联。必须在此处写：Spring Security 的安全链一结束就清空 {@code SecurityContextHolder}，外层 {@code
 * BoneRequestContextFilter} 的 {@code finally} 已读不到身份；MDC 的清理由该过滤器统一负责，本类不清理。
 */
@Component
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    super(jwtTokenService, jwtConfig);
  }

  @Override
  protected void onAuthenticated(JwtPrincipal principal, HttpServletRequest request) {
    putIfPresent(BoneRequestContextFilter.MDC_USER_ID, principal.userId());
    // 租户取 token 内已签名 claim；该头此时也已被本过滤器归一化为同一值，二者等价
    putIfPresent(
        BoneRequestContextFilter.MDC_TENANT_ID,
        principal.tenantId() != null ? principal.tenantId() : request.getHeader("X-Tenant-Id"));
  }

  /** 值为空时不写入，避免 MDC 出现 "userId=null" 这类噪声。 */
  private static void putIfPresent(String key, String value) {
    if (value != null && !value.isBlank()) {
      MDC.put(key, value);
    }
  }
}
