package com.bone.iam.domain.gateway;

import com.bone.core.security.jwt.JwtPrincipal;
import java.util.List;
import java.util.Optional;

/**
 * 访问令牌端口（JWT 的签发与解析，由 infrastructure 实现）。
 *
 * <p>解析 / 去前缀能力收在端口内，使入站适配器（{@code AuthController}）只依赖本端口而不必注入 {@code infrastructure}
 * 实现——E-10.1：adapter 不得依赖 infrastructure。
 */
public interface AccessTokenIssuer {

  String issueAccessToken(Long accountId, String username, Long tenantId, List<String> scopes);

  /** 解析令牌（可含 {@code Bearer } 前缀）；非法 / 过期返回 {@code Optional.empty()}。 */
  Optional<JwtPrincipal> parse(String rawToken);

  /** 去掉 {@code Bearer } 前缀，返回裸令牌。 */
  String stripBearerToken(String token);
}
