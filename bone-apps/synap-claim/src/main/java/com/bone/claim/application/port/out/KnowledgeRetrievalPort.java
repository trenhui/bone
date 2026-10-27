package com.bone.claim.application.port.out;

import java.math.BigDecimal;

/**
 * 知识检索端口（出站，对接 bone-ai C6 受控 RAG / GraphRAG E3：医学目录、条款语义、临床合理性）。
 *
 * <p>一期本地桩返回"临床合理、置信度增量 0"；二期接混合检索 + RRF + rerank + 引用强制 + GroundingGuard。
 * 输出只给"是否临床合理"与置信度增量，结论仍由确定性分诊计算。
 */
public interface KnowledgeRetrievalPort {

  KnowledgeResult match(ClaimContextPort.ClaimContextView ctx);

  record KnowledgeResult(boolean clinicallyReasonable, BigDecimal confidenceDelta, String basis) {}
}
