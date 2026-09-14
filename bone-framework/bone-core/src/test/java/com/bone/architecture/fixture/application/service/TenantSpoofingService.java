package com.bone.architecture.fixture.application.service;

import com.bone.core.tenant.context.TenantContext;

/** 规则单测夹具：应用服务直读 TenantContext（businessLayersMustNotReadTenantContextDirectly 违规）。 */
public class TenantSpoofingService {

  public String currentTenant() {
    return TenantContext.getTenantId();
  }
}
