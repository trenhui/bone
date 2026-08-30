package com.bone.metadata.catalog.infrastructure.tenant;

import com.bone.core.tenant.context.TenantContext;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;

/**
 * 租户上下文适配器：经 {@link TenantProvider} 端口向应用层提供当前租户 ID（基础设施实现）。
 *
 * <p>替代原静态工具类 {@code common.CatalogTenantSupport}：静态调用让应用层隐式依赖基础设施且不可 mock， 改为端口-适配器后与 {@code
 * CatalogIdempotencyStore} 等既有端口保持同一模式。
 *
 * <p>命名约定：端口-适配器实现类统一 {@code Adapter} / {@code Impl} 后缀，保证「接口 → 实现」可一键跳转。
 *
 * <p><b>本模块约定</b>：{@code infrastructure} 下的实现不带 {@code @Component}，统一由 {@code
 * config/CatalogInfrastructureConfiguration} 以 {@code @Bean} 显式装配（与 {@code
 * InMemoryCatalogIdempotencyStore} 等一致）。
 */
public class TenantProviderAdapter implements TenantProvider {

  /** 建模 catalog 的默认租户（无 TenantContext 时回退 1，沿用原 CatalogTenantSupport 语义）。 */
  private static final long DEFAULT_TENANT_ID = 1L;

  @Override
  public long currentTenantId() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
  }
}
