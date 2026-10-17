package com.bone.airag.infrastructure.gateway;

import com.bone.airag.domain.gateway.TenantProvider;
import com.bone.core.exception.BizException;
import com.bone.core.tenant.context.TenantContext;

/**
 * 从 {@link TenantContext} 解析当前租户（infrastructure 是唯一允许直连 TenantContext 的层）。
 *
 * <p><b>fail-closed（2026-10-17 复核修复）</b>：上下文缺失 / 非数值时抛 {@link #ERROR_TENANT_MISSING}——
 * 无租户边界的全库检索是越权读取， 静默返回空列表则把「没接鉴权」伪装成「没有数据」，两者都比明确报错更糟。
 */
public class TenantContextProviderAdapter implements TenantProvider {

  /** 检索租户上下文缺失稳定业务码（fail-closed）。 */
  public static final String ERROR_TENANT_MISSING = "AI_RAG_TENANT_CONTEXT_MISSING";

  @Override
  public Long currentTenantId() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new BizException(500, "检索租户上下文缺失或非数值（拒绝执行无租户边界的全库检索）", ERROR_TENANT_MISSING);
    }
    return tenantId;
  }
}
