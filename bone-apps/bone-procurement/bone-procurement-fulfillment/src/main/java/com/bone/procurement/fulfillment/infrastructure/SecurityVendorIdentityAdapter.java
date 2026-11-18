package com.bone.procurement.fulfillment.infrastructure;

import com.bone.procurement.fulfillment.domain.port.VendorIdentityPort;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 供应商身份适配器：优先从 Spring Security 认证主体解析 partyId；无认证主体时回退到配置的 dev 身份。
 *
 * <p>生产必须配置真实认证；dev 回退值仅用于本地联调/E2E，且显式配置才生效——未配置则 fail-closed（返回 empty ⇒ 403）。
 */
@Component
public class SecurityVendorIdentityAdapter implements VendorIdentityPort {

  private final String devVendorPartyId;

  public SecurityVendorIdentityAdapter(
      @Value("${bone.procurement.fulfillment.dev-vendor-party-id:}") String devVendorPartyId) {
    this.devVendorPartyId = devVendorPartyId;
  }

  @Override
  public Optional<String> currentVendorPartyId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
      String name = auth.getName();
      if (!"anonymousUser".equals(name) && !name.isBlank()) {
        return Optional.of(name);
      }
    }
    return devVendorPartyId == null || devVendorPartyId.isBlank()
        ? Optional.empty()
        : Optional.of(devVendorPartyId);
  }
}
