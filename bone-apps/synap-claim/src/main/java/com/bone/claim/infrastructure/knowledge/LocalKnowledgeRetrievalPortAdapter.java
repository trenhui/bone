package com.bone.claim.infrastructure.knowledge;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 知识检索适配器（一期本地桩：默认临床合理、置信度增量 0）。
 *
 * <p>二期接 bone-ai C6 受控 RAG / GraphRAG E3（混合检索 + RRF + rerank + 引用强制 + GroundingGuard），
 * 支撑诊断-用药-诊疗矛盾等临床合理性判定。
 */
@Component
public class LocalKnowledgeRetrievalPortAdapter implements KnowledgeRetrievalPort {

  @Override
  public KnowledgeResult match(ClaimContextPort.ClaimContextView ctx) {
    return new KnowledgeResult(true, BigDecimal.ZERO, "local-stub");
  }
}
