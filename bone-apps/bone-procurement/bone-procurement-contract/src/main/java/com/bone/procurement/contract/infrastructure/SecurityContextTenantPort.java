package com.bone.procurement.contract.infrastructure;

import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.core.tenant.context.TenantContext;
import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.domain.gateway.ContractReviewTenantPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从登录态（JWT principal / TenantContext）取当前租户，租户不接受请求自报。 */
@Component
@ConditionalOnContractAiReviewEnabled
public class SecurityContextTenantPort implements ContractReviewTenantPort {

  @Override
  public String currentTenantId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof JwtPrincipal principal
        && principal.tenantId() != null
        && !principal.tenantId().isBlank()) {
      return principal.tenantId();
    }
    Long fromContext = TenantContext.getTenantIdAsLong();
    return fromContext == null ? null : String.valueOf(fromContext);
  }
}
