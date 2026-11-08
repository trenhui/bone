package com.bone.claim.application.port.out;

/**
 * 租户上下文端口（出站）。
 *
 * <p>业务层禁止直连 {@code com.bone.core.tenant.context.TenantContext}（ArchUnit {@code
 * businessLayersMustNotReadTenantContextDirectly}），统一经本端口获取当前租户。
 */
public interface TenantPort {

  /** 当前租户 ID（认证主体为唯一权威来源，§10.3）。 */
  long currentTenantId();
}
