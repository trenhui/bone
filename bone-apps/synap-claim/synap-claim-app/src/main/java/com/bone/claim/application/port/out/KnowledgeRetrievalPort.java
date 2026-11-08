package com.bone.claim.application.port.out;

import java.math.BigDecimal;

/**
 * 知识检索端口（出站，一期目录准入核验，二期接 bone-ai C6 受控 RAG / GraphRAG E3）。
 *
 * <p><b>AI 原生契约</b>：知识层是 AI 认知的第二个输入源，与 {@link AiGatewayPort} 的结构化认知并列。 一期实现 {@code
 * CatalogKnowledgeRetrievalPortAdapter} 消费 {@link DocumentParsePort} 产出的结构化事实，
 * 在租户医学目录中做受控准入核验；二期换混合检索 + RRF + rerank + 引用强制 + GroundingGuard 时端口不变。
 *
 * <p><b>语义边界（必须写进 {@code basis}）</b>：{@code clinicallyReasonable=true} 表示「在 {@code basis}
 * 声明的受控核验范围内未发现差异」，不是「模型确认临床合理」。一期范围仅为目录准入与事实齐备性， 不含临床推理/适应症图谱。范围外一律 {@code
 * available=false}，不得据此宣称已核验。
 *
 * <p><b>fail-closed 契约</b>：当 {@code available=false}（事实缺失 / 目录未命中 / 能力未集成）时， {@code
 * clinicallyReasonable} 不得被当作"已确认合理"或"已确认不合理"—— 无法判定即由消费方转人工（与反欺诈闸同原则），绝不映射为对客户不利的结论。
 */
public interface KnowledgeRetrievalPort {

  KnowledgeResult match(ClaimContextPort.ClaimContextView ctx);

  /** 部署态声明兜底：适配器未覆盖时一律 UNKNOWN（fail-closed），不得推断为 READY。 */
  default AiCapabilityDeclaration declaration() {
    return AiCapabilityDeclaration.unknown("E3_KNOWLEDGE");
  }

  record KnowledgeResult(
      boolean clinicallyReasonable, BigDecimal confidenceDelta, String basis, boolean available) {}
}
