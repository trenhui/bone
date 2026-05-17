package com.bone.metadata.catalog.common;

import com.bone.core.tenant.context.TenantContext;

/** 建模 catalog 默认租户（无 TenantContext 时回退 1） */
public final class CatalogTenantSupport {

  private CatalogTenantSupport() {}

  public static long currentTenantId() {
    Long tenantId = TenantContext.getTenantId();
    return tenantId != null ? tenantId : 1L;
  }
}
