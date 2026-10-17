package com.bone.aiapp.config;

import com.bone.core.exception.BizException;
import com.bone.core.tenant.context.TenantContext;
import org.springframework.stereotype.Component;

/**
 * 生产拓扑租户解析：一律从 {@link TenantContext} 取（详设 §3.1「租户从上下文取，禁止入参传租户」）。
 *
 * <p><b>为何不再信请求体（2026-10-17 复核修复）</b>：租户是安全边界不是业务参数——调用方改一个 body 字段就能以别家租户身份 调 AI 能力（规则库 / 检索库 /
 * HITL 队列全是租户隔离数据）。上下文缺失时 fail-closed 抛错，让「没接鉴权」在第一次调用就暴露， 而不是静默接受任意租户。
 *
 * <p>部署前提：bone-ai-app 前方须有写入 TenantContext 的 JWT / 租户过滤器（bone-security + bone-web 形态）； 未接入时所有端点返回
 * {@link #ERROR_TENANT_MISSING}，属预期行为而非缺陷。
 */
@Component
public class AiTenantContext {

  /** 租户上下文缺失稳定业务码（fail-closed：不接受入参传租户）。 */
  public static final String ERROR_TENANT_MISSING = "AI_APP_TENANT_CONTEXT_MISSING";

  /** 取当前租户；上下文未注入时抛稳定码业务异常（拒绝以匿名 / 伪租户身份调用）。 */
  public String requireTenantId() {
    String tenantId = TenantContext.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      throw new BizException(
          400,
          "租户上下文缺失：请经鉴权链路（JWT / X-Tenant-Id 过滤器）注入 TenantContext，禁止入参传租户",
          ERROR_TENANT_MISSING);
    }
    return tenantId;
  }

  /**
   * 取当前租户的数值形态（Long 租户列的领域模型用，如 {@code ExternalCall} / {@code AlertEvent}）。
   *
   * <p>非数值编码（如 {@code system}）显式失败——这些表的 {@code tenant_id} 是 BIGINT，静默丢租户会让审计留痕失真。
   */
  public Long requireNumericTenantId() {
    String tenantId = requireTenantId();
    try {
      return Long.valueOf(tenantId.trim());
    } catch (NumberFormatException e) {
      throw new BizException(400, "租户标识非数值编码，无法写入数值租户列: " + tenantId, ERROR_TENANT_MISSING);
    }
  }
}
