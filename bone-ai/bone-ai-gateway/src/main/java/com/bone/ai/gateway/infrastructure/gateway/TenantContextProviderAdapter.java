package com.bone.ai.gateway.infrastructure.gateway;

import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.core.tenant.context.TenantContext;

/** 从 {@link TenantContext} 解析当前租户（infrastructure 是唯一允许直连 TenantContext 的层）。 */
public class TenantContextProviderAdapter implements TenantProvider {

  @Override
  public String currentTenantId() {
    return TenantContext.getTenantId();
  }
}
