package com.bone.blueprint.infrastructure.security;

import com.bone.core.security.auth.AbstractJwtAuthenticationFilter;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.security.jwt.JwtTokenService;
import org.springframework.stereotype.Component;

/**
 * 订单 / 支付样例的 JWT 认证过滤器。
 *
 * <p><b>与平台同范式</b>：继承框架 {@link AbstractJwtAuthenticationFilter}，只做一件事——把 {@code Authorization:
 * Bearer} token 解析成 {@code JwtPrincipal} 写入 Spring Security 上下文。其余 7 个模块 （{@code bone-system} /
 * {@code bone-integration} / {@code bone-metadata-server} / …）的写法与此完全一致。
 *
 * <p><b>为什么不自己做租户绑定</b>：租户由请求头 {@code X-Tenant-Id} 承载（见 {@code TenantContextFilter}）。 生产环境该头由网关
 * {@code bone-gateway} 的 {@code JwtAuthGlobalFilter} 用 token 内<strong>已签名</strong>的 {@code
 * tenantId} claim 覆盖后下发，客户端无法伪造；直连模块端口时才依赖调用方自律。同理，本类也不在此处做租户与 claim
 * 的交叉校验——那是网关的职责，模块侧重放会让两处规则各自演化。
 *
 * <p><b>与 IAM 的关系：不调用 IAM</b>。IAM 负责签发 token（{@code AuthController}）与账号管理，各模块只用共享密钥 （{@code
 * bone.iam.jwt.secret-key}）做<strong>本地离线验签</strong>。若改成每次请求回调 IAM，就把 IAM 变成了所有
 * 服务的可用性单点，也会给每次调用加上一跳额外延迟。
 */
@Component
public class JwtAuthenticationFilter extends AbstractJwtAuthenticationFilter {

  public JwtAuthenticationFilter(JwtTokenService jwtTokenService, JwtConfig jwtConfig) {
    super(jwtTokenService, jwtConfig);
  }
}
