package com.bone.aitrace.infrastructure.gateway;

import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.core.tenant.context.TenantContext;

/** 从 {@link TenantContext} 解析当前租户（infrastructure 是唯一允许直连 TenantContext 的层，E-2）。 */
public class TenantContextProviderAdapter implements TenantProvider {

  @Override
  public Long currentTenantId() {
    return TenantContext.getTenantIdAsLong();
  }
}
