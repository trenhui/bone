package com.bone.metadata.engine.multi;

import com.bone.core.tenant.context.TenantContext;

/**
 * 默认多租户上下文持有者实现。
 *
 * <p>委托到 {@link TenantContext}（bone-core 唯一标准实现），不再维护独立的 ThreadLocal， 确保全项目租户上下文一致。
 */
public class DefaultMultiTenantContextHolder implements MultiTenantContextHolder {

  @Override
  public String getCurrentTenantId() {
    String tenantId = TenantContext.getTenantId();
    return tenantId != null ? tenantId : getDefaultTenantId();
  }

  @Override
  public void setCurrentTenantId(String tenantId) {
    if (tenantId != null && !tenantId.trim().isEmpty()) {
      TenantContext.setTenantId(tenantId);
    } else {
      clear();
    }
  }

  @Override
  public void clear() {
    TenantContext.clear();
  }

  @Override
  public String getDefaultTenantId() {
    return "default";
  }
}
