package com.bone.metadata.catalog.infrastructure.gateway;

import com.bone.metadata.catalog.domain.gateway.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring Security 上下文解析当前用户 ID。
 *
 * <p>数据来源：{@code JwtAuthenticationFilter} 在认证成功后把 JWT 的 {@code userId} claim（字符串化账号 ID）放入 {@code
 * Authentication#details}。API-Key / 匿名主体无 details → 返回 {@code null}（无主体场景，G1② 豁免）。
 */
public class SecurityContextCurrentUserAdapter implements CurrentUserProvider {

  @Override
  public Long currentUserIdOrNull() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth.getDetails() == null) {
      return null;
    }
    try {
      return Long.valueOf(String.valueOf(auth.getDetails()));
    } catch (NumberFormatException e) {
      // details 不是可解析的用户 ID（如匿名主体的字符串 principal）→ 视为无主体
      return null;
    }
  }
}
