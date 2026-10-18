package com.bone.airag.infrastructure.gateway;

import com.bone.airag.common.AiRagErrorCodes;
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

  /**
   * 检索租户上下文缺失稳定业务码（fail-closed）。
   *
   * <p>2026-10-18：真源已收敛至 {@link AiRagErrorCodes}——i18n 一致性门禁只扫 {@code *ErrorCodes.java}，
   * 码散落在适配器里时台账与代码字符串的一致性实际未被校验（假安全）。此处保留同名字段作委托，调用点零改动。
   */
  @Deprecated
  public static final String ERROR_TENANT_MISSING = AiRagErrorCodes.TENANT_CONTEXT_MISSING;

  @Override
  public Long currentTenantId() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new BizException(500, "检索租户上下文缺失或非数值（拒绝执行无租户边界的全库检索）", ERROR_TENANT_MISSING);
    }
    return tenantId;
  }
}
