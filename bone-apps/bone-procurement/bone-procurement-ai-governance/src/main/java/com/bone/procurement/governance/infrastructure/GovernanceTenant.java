package com.bone.procurement.governance.infrastructure;

import com.bone.core.tenant.context.TenantContext;

/** 治理域租户取值：基础设施层只读平台登录态；缺失即 fail-closed，禁止回退到系统缺省租户。 */
final class GovernanceTenant {

  private GovernanceTenant() {}

  static String current() {
    Long id = TenantContext.getTenantIdAsLong();
    if (id == null) {
      throw new IllegalStateException("需要登录租户上下文，治理域禁止缺省租户");
    }
    return String.valueOf(id);
  }
}
