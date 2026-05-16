package com.bone.metadata.engine.multi;

/** 默认多租户上下文持有者实现 提供基本的租户ID管理功能 */
public class DefaultMultiTenantContextHolder implements MultiTenantContextHolder {

  private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
  private static final String DEFAULT_TENANT_ID = "default";

  @Override
  public String getCurrentTenantId() {
    String tenantId = CONTEXT.get();
    return tenantId != null ? tenantId : getDefaultTenantId();
  }

  @Override
  public void setCurrentTenantId(String tenantId) {
    if (tenantId != null && !tenantId.trim().isEmpty()) {
      CONTEXT.set(tenantId);
    } else {
      clear();
    }
  }

  @Override
  public void clear() {
    CONTEXT.remove();
  }

  @Override
  public String getDefaultTenantId() {
    return DEFAULT_TENANT_ID;
  }
}
