package com.bone.blueprint.infrastructure.context;

import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.core.tenant.context.TenantContext;
import org.springframework.stereotype.Component;

/** 租户上下文适配器：经 {@link TenantProvider} 端口向应用层提供当前租户 ID（基础设施实现）。 */
@Component
public class TenantSupport implements TenantProvider {

  private static final long DEFAULT_TENANT_ID = 0L;

  @Override
  public long currentTenantId() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
  }
}
