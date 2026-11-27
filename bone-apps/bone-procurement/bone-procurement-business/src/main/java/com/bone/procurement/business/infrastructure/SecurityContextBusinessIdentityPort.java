package com.bone.procurement.business.infrastructure;

import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.tenant.context.TenantContext;
import com.bone.procurement.business.domain.gateway.BusinessIdentityPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从登录态解析租户与操作人；匿名、缺租户、缺操作人返回 null，调用方 fail-closed。 */
@Component
public class SecurityContextBusinessIdentityPort implements BusinessIdentityPort {

  @Override
  public Actor current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    Long tenantId = null;
    String operator = null;
    if (authentication.getPrincipal() instanceof JwtPrincipal principal) {
      tenantId = parseTenant(principal.tenantId());
      operator = firstNonBlank(principal.username(), principal.userId());
    }
    if (tenantId == null) {
      tenantId = TenantContext.getTenantIdAsLong();
    }
    operator = firstNonBlank(operator, authentication.getName());
    if (tenantId == null || operator == null) {
      return null;
    }
    return new Actor(tenantId, operator);
  }

  private static Long parseTenant(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.valueOf(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank() && !"anonymousUser".equals(value)) {
        return value.trim();
      }
    }
    return null;
  }
}
