package com.bone.claim.application.handler;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayUnavailableException;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.NotificationPort;
import com.bone.claim.domain.model.decision.DecisionGraph;
import com.bone.claim.domain.model.decision.DecisionNodeStatus;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 决策节点 {@code AiAssess}（LLM 类）：AI 评估 + 临床合理性（C6 GraphRAG E3）+ 风险预警（C9）。
 *
 * <p>网关不可用 → 抛 {@link AiGatewayUnavailableException}，由执行器降级纯规则路径（FALLBACK），置信度回退到 RuleCheck 的
 * ruleConfidence。 临床不合理或 AI 建议拒赔 → {@code rejectSuggested=true}。风险/拒赔结论经 {@link NotificationPort}
 * 发预警（C9）。AI 只产建议与置信度，"AI 不碰钱"。
 */
@Component
public class AiAssessNodeHandler implements DecisionNodeHandler {

  private final ClaimContextPort claimContextPort;
  private final AiGatewayPort aiGatewayPort;
  private final KnowledgeRetrievalPort knowledgeRetrievalPort;
  private final NotificationPort notificationPort;

  public AiAssessNodeHandler(
      ClaimContextPort claimContextPort,
      AiGatewayPort aiGatewayPort,
      KnowledgeRetrievalPort knowledgeRetrievalPort,
      NotificationPort notificationPort) {
    this.claimContextPort = claimContextPort;
    this.aiGatewayPort = aiGatewayPort;
    this.knowledgeRetrievalPort = knowledgeRetrievalPort;
    this.notificationPort = notificationPort;
  }

  @Override
  public String nodeName() {
    return DecisionGraph.AI_ASSESS;
  }

  @Override
  public DecisionNodeOutcome execute(DecisionNodeContext context) {
    ClaimContextPort.ClaimContextView v = claimContextPort.getContext(context.claimId());
    KnowledgeRetrievalPort.KnowledgeResult k = knowledgeRetrievalPort.match(v);

    boolean rejectSuggested;
    BigDecimal confidence;
    try {
      AiGatewayPort.AiAssessment a = aiGatewayPort.assess(v);
      rejectSuggested = a.rejectSuggested() || !k.clinicallyReasonable();
      confidence = a.confidence();
    } catch (AiGatewayUnavailableException ex) {
      // 执行器据此节点类型为 LLM → 记 FALLBACK，confidence 回退 ruleConfidence
      rejectSuggested = !k.clinicallyReasonable();
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("rejectSuggested", rejectSuggested);
      if (rejectSuggested) {
        notificationPort.alert("WARN", "AI_RISK", v, out);
      }
      return new DecisionNodeOutcome(
          DecisionNodeStatus.FALLBACK, out, null, "LLM_UNAVAILABLE", "LLM_UNAVAILABLE");
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rejectSuggested", rejectSuggested);
    if (rejectSuggested) {
      notificationPort.alert("WARN", "AI_RISK", v, out);
    }
    return DecisionNodeOutcome.success(out, confidence, "ai assess", "OK");
  }
}
