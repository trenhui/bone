package com.bone.claim.infrastructure.knowledge;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 知识检索适配器（一期本地桩：能力未集成）。
 *
 * <p>二期接 bone-ai C6 受控 RAG / GraphRAG E3（混合检索 + RRF + rerank + 引用强制 + GroundingGuard），
 * 支撑诊断-用药-诊疗矛盾等临床合理性判定。
 *
 * <p><b>诚实声明</b>：本桩未执行任何临床合理性核验，{@code available=false}； {@code clinicallyReasonable} 置为 false
 * 仅表示"未确认合理"，<b>绝不</b>可解读为"已核验无矛盾"。消费方据此 fail-closed 转人工 （见 {@code
 * AiAssessNodeHandler}），杜绝"静默放行临床合理性"假绿。
 */
@Component
public class LocalKnowledgeRetrievalPortAdapter implements KnowledgeRetrievalPort {

  @Override
  public KnowledgeResult match(ClaimContextPort.ClaimContextView ctx) {
    return new KnowledgeResult(false, BigDecimal.ZERO, "local-stub-not-integrated", false);
  }
}
