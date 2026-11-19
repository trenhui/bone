package com.bone.procurement.governance.infrastructure;

import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.procurement.governance.application.ConditionalOnAiGovernanceEnabled;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从登录态（JWT principal）取当前操作人；无登录态返回 null，调用方 fail-closed。 */
@Component
@ConditionalOnAiGovernanceEnabled
public class SecurityContextGovernanceActorPort implements GovernanceActorPort {

  @Override
  public String currentActorId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    if (authentication.getPrincipal() instanceof JwtPrincipal principal) {
      return principal.userId() != null && !principal.userId().isBlank()
          ? principal.userId()
          : principal.username();
    }
    String name = authentication.getName();
    return name == null || name.isBlank() || "anonymousUser".equals(name) ? null : name;
  }
}
