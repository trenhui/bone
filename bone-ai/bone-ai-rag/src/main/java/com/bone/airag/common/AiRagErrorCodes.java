package com.bone.airag.common;

/**
 * bone-ai-rag 稳定错误码（前端 i18n 键 + 监控聚合口径，已登记 Bone-错误码登记.md §6 与两份语言包）。
 *
 * <p><b>为何从 {@code TenantContextProviderAdapter} 收敛到这里（2026-10-18）</b>：i18n 一致性门禁只扫描 {@code
 * *ErrorCodes.java} 类，码定义在适配器私有常量里时，台账与代码字符串的一致性实际未被校验（假安全）。收敛到本类让门禁扫得到。
 */
public final class AiRagErrorCodes {

  private AiRagErrorCodes() {}

  /** 检索请求缺少租户上下文（500）：fail-closed——检索不得在租户不明的情况下执行， 否则结果集可能跨租户泄漏。 */
  public static final String TENANT_CONTEXT_MISSING = "AI_RAG_TENANT_CONTEXT_MISSING";
}
