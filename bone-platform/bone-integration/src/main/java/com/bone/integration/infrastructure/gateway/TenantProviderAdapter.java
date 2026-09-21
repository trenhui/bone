package com.bone.integration.infrastructure.gateway;

import com.bone.core.tenant.context.TenantContext;
import com.bone.integration.domain.gateway.TenantProvider;
import org.springframework.stereotype.Component;

/**
 * 租户上下文适配器：经 {@link TenantProvider} 端口向应用层提供当前租户 ID（E-2）。
 *
 * <p>{@code TenantContext} 只允许在基础设施层直调；application / domain / adapter 一律经端口访问，
 * 使租户读取可被单测 mock，并把审计、传播与空值防护收敛到唯一实现。
 */
@Component
public class TenantProviderAdapter implements TenantProvider {

  @Override
  public Long currentTenantIdOrNull() {
    return TenantContext.getTenantIdAsLong();
  }
}
