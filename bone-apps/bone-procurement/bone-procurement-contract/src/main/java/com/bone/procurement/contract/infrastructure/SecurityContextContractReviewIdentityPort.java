package com.bone.procurement.contract.infrastructure;

import com.bone.core.security.jwt.JwtPrincipal;
import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.domain.gateway.ContractReviewIdentityPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从 SecurityContext 的 JwtPrincipal 解析合同终决复核人；匿名/不可解析返回 null。 */
@Component
@ConditionalOnContractAiReviewEnabled
public class SecurityContextContractReviewIdentityPort implements ContractReviewIdentityPort {

  @Override
  public String currentReviewer() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    String reviewer = null;
    if (authentication.getPrincipal() instanceof JwtPrincipal principal) {
      reviewer = firstNonBlank(principal.username(), principal.userId());
    }
    return firstNonBlank(reviewer, authentication.getName());
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
