package com.bone.studio.generator.infrastructure.gateway;

import com.bone.core.tenant.context.TenantContext;
import com.bone.studio.generator.domain.gateway.TenantProvider;
import org.springframework.stereotype.Component;

/** 从可信租户上下文取当前租户（{@link TenantProvider} 的基础设施实现）。 */
@Component
public class TenantProviderGatewayAdapter implements TenantProvider {

  @Override
  public Long currentTenantIdOrNull() {
    return TenantContext.getTenantIdAsLong();
  }
}
