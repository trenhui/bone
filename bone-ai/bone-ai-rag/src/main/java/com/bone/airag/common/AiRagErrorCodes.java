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

  /**
   * 嵌入推理服务调用 / 响应异常（502）：共享 bge-m3 provider 故障，不静默回落零向量。
   *
   * <p>2026-10-21 补齐：HttpBgeEmbeddingClient 的抛出点与台账（Bone-错误码登记.md §6）均已就位，仅缺本常量定义——缺此常量则该模块无法编译。
   */
  public static final String EMBEDDING_PROVIDER_FAIL = "AI_RAG_EMBEDDING_PROVIDER_FAIL";

  /**
   * 重排序推理服务调用 / 响应异常（502）：共享 bge-reranker provider 故障，不静默回落 RRF 序。
   *
   * <p>2026-10-21 补齐：HttpBgeRerankClient 的抛出点与台账（Bone-错误码登记.md §6）均已就位，仅缺本常量定义——缺此常量则该模块无法编译。
   */
  public static final String RERANK_PROVIDER_FAIL = "AI_RAG_RERANK_PROVIDER_FAIL";
}
