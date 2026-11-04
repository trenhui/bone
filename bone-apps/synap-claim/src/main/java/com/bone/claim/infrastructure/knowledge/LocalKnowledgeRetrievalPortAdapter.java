package com.bone.claim.infrastructure.knowledge;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 知识检索降级桩（仅在 {@code bone.claim.knowledge.mode=stub} 时装配）。
 *
 * <p>默认实现为 {@link CatalogKnowledgeRetrievalPortAdapter}：基于感知层结构化事实与租户医学目录做受控核验。 本桩用于演练「AI
 * 能力层整体不可用」的降级路径，以及无需目录数据的单元夹具； 二期接 bone-ai C6 受控 RAG / GraphRAG E3 后，两种实现都由同一端口契约替换。
 *
 * <p><b>诚实声明</b>：本桩未执行任何核验，{@code available=false}； {@code clinicallyReasonable} 置为 false
 * 仅表示"未确认合理"，<b>绝不</b>可解读为"已核验无矛盾"。消费方据此 fail-closed 转人工 （见 {@code
 * AiAssessNodeHandler}），杜绝"静默放行临床合理性"假绿。
 */
@Component
@ConditionalOnProperty(name = "bone.claim.knowledge.mode", havingValue = "stub")
public class LocalKnowledgeRetrievalPortAdapter implements KnowledgeRetrievalPort {

  @Override
  public KnowledgeResult match(ClaimContextPort.ClaimContextView ctx) {
    return new KnowledgeResult(false, BigDecimal.ZERO, "local-stub-not-integrated", false);
  }
}
