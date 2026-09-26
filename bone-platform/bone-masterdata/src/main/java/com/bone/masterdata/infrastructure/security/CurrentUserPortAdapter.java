package com.bone.masterdata.infrastructure.security;

import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.tenant.context.TenantContext;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * {@link CurrentUserPort} 的 Spring Security 实现：主体来自 {@code AbstractJwtAuthenticationFilter} 写入的
 * {@link JwtPrincipal}。
 */
@Component
public class CurrentUserPortAdapter implements CurrentUserPort {

  @Override
  public Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof JwtPrincipal principal
        && principal.userId() != null
        && !principal.userId().isBlank()) {
      try {
        return Long.valueOf(principal.userId());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  @Override
  public Long requireUserId() {
    Long userId = currentUserId();
    if (userId == null) {
      throw MasterDataErrors.of(MasterDataErrorCodes.USER_CONTEXT_REQUIRED, "该操作需要登录上下文（JWT）");
    }
    return userId;
  }

  @Override
  public Long currentTenantId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof JwtPrincipal principal
        && principal.tenantId() != null) {
      try {
        return Long.valueOf(principal.tenantId());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    // 回退 SDK TenantContext：与租户过滤同源（后台任务/测试仅装配 ThreadLocal 的场景）
    return TenantContext.getTenantIdAsLong();
  }

  @Override
  public Long requireTenantId() {
    Long tenantId = currentTenantId();
    if (tenantId == null) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.USER_CONTEXT_REQUIRED, "该操作需要租户上下文（JWT tenant claim）");
    }
    return tenantId;
  }
}
