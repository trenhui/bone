package com.bone.procurement.qualification.infrastructure;

import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.tenant.context.TenantContext;
import com.bone.procurement.qualification.domain.gateway.ReviewIdentityPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 从登录态解析人工终决身份：租户/操作人取 JWT principal（结构由框架校验），回退 TenantContext 仅作为可信线程上下文， 绝不读取请求体。缺登录态或缺租户返回
 * null，调用方 fail-closed。
 */
@Component
public class SecurityContextReviewIdentityPort implements ReviewIdentityPort {

  @Override
  public ReviewIdentity current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    Long tenantId = null;
    String reviewer = null;
    if (authentication.getPrincipal() instanceof JwtPrincipal principal) {
      tenantId = parseTenant(principal.tenantId());
      reviewer = firstNonBlank(principal.username(), principal.userId());
    }
    if (tenantId == null) {
      tenantId = TenantContext.getTenantIdAsLong();
    }
    if (tenantId == null || firstNonBlank(reviewer, authentication.getName()) == null) {
      return null;
    }
    return new ReviewIdentity(tenantId, firstNonBlank(reviewer, authentication.getName()));
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
