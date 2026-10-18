package com.bone.aiapp.config;

/**
 * bone-ai-app 稳定错误码（前端 i18n 键 + 监控聚合口径，已登记 Bone-错误码登记.md §6 与两份语言包）。
 *
 * <p><b>为何从 {@link AiTenantContext} 收敛到这里（2026-10-18）</b>：i18n 一致性门禁只扫描 {@code *ErrorCodes.java}
 * 类，码定义在上下文工具类里时，台账与代码字符串的一致性实际未被校验（假安全）。收敛到本类让门禁扫得到。
 */
public final class AiAppErrorCodes {

  private AiAppErrorCodes() {}

  /** 请求缺少租户上下文（400）：所有 AI 端点 fail-closed——无人能确定租户即拒绝执行， 不允许「默认租户」这种静默降级。 */
  public static final String TENANT_CONTEXT_MISSING = "AI_APP_TENANT_CONTEXT_MISSING";
}
