package com.bone.blueprint.infrastructure.context;

import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.core.tenant.context.TenantContext;
import org.springframework.stereotype.Component;

/**
 * 租户上下文适配器：经 {@link TenantProvider} 端口向应用层提供当前租户 ID（基础设施实现）。
 *
 * <p><b>命名约定</b>：端口-适配器架构中，实现类统一用 {@code <Port>Adapter} 或 {@code <Port>Impl} 后缀， 使「接口 →
 * 实现」可一键跳转、可全局搜索。原名 {@code TenantSupport} 与端口名不一致，团队按接口名搜实现类会搜不到。
 */
@Component
public class TenantProviderAdapter implements TenantProvider {

  private static final long DEFAULT_TENANT_ID = 0L;

  @Override
  public long currentTenantId() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
  }
}
