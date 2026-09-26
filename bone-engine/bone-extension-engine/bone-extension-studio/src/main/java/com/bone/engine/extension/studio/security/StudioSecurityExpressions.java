package com.bone.engine.extension.studio.security;

import com.bone.engine.extension.studio.config.ExtensionStudioProperties;
import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** {@code @PreAuthorize("@studioSecurity.hasScope('...')")} 求值 Bean。 */
@Component("studioSecurity")
public class StudioSecurityExpressions {

  private final ExtensionStudioProperties studioProperties;

  public StudioSecurityExpressions(ExtensionStudioProperties studioProperties) {
    this.studioProperties = studioProperties;
  }

  public boolean hasScope(String scope) {
    if (studioProperties.getSecurity().isPermitUnauthenticated()) {
      return true;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }
    Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
    if (authorities == null || authorities.isEmpty()) {
      return false;
    }
    for (GrantedAuthority authority : authorities) {
      String value = authority.getAuthority();
      if ("*".equals(value) || scope.equals(value) || ("SCOPE_" + scope).equals(value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 多 Scope 兼容判定（5a G3 过渡期回退）：scopes[0] 为新 Scope，其余为过渡期兼容的旧 Scope。 {@code
   * security.legacy-scope-fallback=false}（切流）后仅认新 Scope。
   */
  public boolean hasAnyScope(String... scopes) {
    if (scopes == null || scopes.length == 0) {
      return false;
    }
    if (!studioProperties.getSecurity().isLegacyScopeFallback()) {
      return hasScope(scopes[0]);
    }
    for (String scope : scopes) {
      if (hasScope(scope)) {
        return true;
      }
    }
    return false;
  }
}
