package com.bone.metadata.security;

import com.bone.metadata.runtime.domain.CurrentRoleProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 从 Spring Security 上下文解析当前用户角色码。
 *
 * <p>数据来源：{@link JwtAuthenticationFilter} 把 JWT 的 {@code roleCodes} claim 以 {@code ROLE_} 前缀
 * （Spring {@code hasRole} 惯例）写入 authorities。本适配器反向前缀提取——与权限码（{@code runtime:x:read} 形状、无前缀）同list
 * 存放但命名空间不重叠，{@code hasAuthority} 判权限码的消费方不会误中。
 *
 * <p>无认证 / 匿名主体返回 {@code null}（内部受信语义，见 {@link CurrentRoleProvider} 三态契约）。
 */
@Component
public class SecurityContextCurrentRoleAdapter implements CurrentRoleProvider {

  /** 角色 authority 前缀（与 Spring hasRole 惯例一致；IAM 侧角色码无此前缀，在此统一加减）。 */
  static final String ROLE_PREFIX = "ROLE_";

  @Override
  public List<String> currentRoleCodesOrNull() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return null;
    }
    List<String> codes = new ArrayList<>();
    for (GrantedAuthority a : auth.getAuthorities()) {
      String authority = a.getAuthority();
      if (authority != null && authority.startsWith(ROLE_PREFIX)) {
        String code = authority.substring(ROLE_PREFIX.length());
        if (!code.isBlank()) {
          codes.add(code);
        }
      }
    }
    return codes;
  }
}
