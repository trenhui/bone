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

  /**
   * 检索作用域切片数超容量护栏（422，fail-closed）：过渡态全量载入内存打分，超限即拒， 提示调用方收窄 {@code doc_scope} 或调大 {@code
   * bone.ai.rag.max-scan}——绝不静默截断（截断会使「已检索全集」的假设不成立，引用完整性失真）。
   *
   * <p>2026-10-23 新增：容量护栏为 RAG 诊断 P0 修复项之一（《RAG方案诊断与优化-2026-10-23》S1）。
   */
  public static final String SCOPE_OVER_LIMIT = "AI_RAG_SCOPE_OVER_LIMIT";

  /**
   * 文档不在当前调用方可见范围（403，fail-closed）：接入方提供了文档级 ACL（{@code RagAclPort} 返回受限集合），
   * 而显式 {@code doc_scope} 无 ACL 归属——绝不静默回落全租户检索（那会把越权请求变成合法请求）。
   *
   * <p>2026-10-23 新增：RAG 诊断 P1-7（文档级 ACL seam + fail-closed）。默认无 ACL 模型时本码不会触发
   * （仅租户硬隔离兜底）；接入方提供 ACL 实现后生效。
   */
  public static final String DOC_FORBIDDEN = "AI_RAG_DOC_FORBIDDEN";

  /**
   * 生成答案接地校验未通过（422；{@code GroundingGuard} 判定 {@code grounded=false}）。
   *
   * <p>三种触发面：① 答案无任何引用标记（违反强制引用契约）；② 引用了不存在的序号（幻觉引用，如上下文只有 2 条却写 [9]）；
   * ③ 存在无上下文支撑的句子（编造陈述，或句中数字在上下文里找不到）。
   *
   * <p>语义是「拒答」而非「服务异常」：调用方应把它当作可控合规结果返回（附 {@code GroundingVerdict} 说明原因），
   * 不要重试——重试只会生成另一段同样无支撑的内容。
   */
  public static final String GROUNDING_FAILED = "AI_RAG_GROUNDING_FAILED";

  /**
   * 答案生成服务未接入（503；{@code UnavailableAnswerGeneration} 默认实现抛出）。
   *
   * <p>不是「模型报错」，而是「根本没人实现生成端口」——宁可启动后首次调用就显式失败，也不要静默返回空串让故障混进
   * 业务结果。接入方在组合根以 {@code @Bean AnswerGenerationPort} 覆盖后本码不再出现。
   */
  public static final String ANSWER_GENERATION_UNAVAILABLE = "AI_RAG_ANSWER_GENERATION_UNAVAILABLE";
}
