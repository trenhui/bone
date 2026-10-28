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
import java.util.List;
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
    // 临床合理性闸：仅当能力真实可用且确认合理，knowledgeConfirmed 才为真；
    // 能力未集成（available=false）即"无法判定"，由消费方 fail-closed 转人工（与反欺诈闸同原则）。
    boolean knowledgeConfirmed = k.available() && k.clinicallyReasonable();

    boolean rejectSuggested;
    BigDecimal confidence;
    AiGatewayPort.AiAssessment a = null;
    try {
      a = aiGatewayPort.assess(v);
      rejectSuggested = a.rejectSuggested() || !knowledgeConfirmed;
      confidence = a.confidence();
    } catch (AiGatewayUnavailableException ex) {
      // 执行器据此节点类型为 LLM → 记 FALLBACK，confidence 回退 ruleConfidence
      rejectSuggested = !knowledgeConfirmed;
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
    // 结构化拒赔原因码随轨迹落库（可解释性 / 审计 / 申诉）：不利决定须可解释，自由文本理由已在前置层禁用
    out.put("rejectReasons", a == null ? List.of() : a.rejectReasons());
    if (rejectSuggested) {
      notificationPort.alert("WARN", "AI_RISK", v, out);
    }
    return DecisionNodeOutcome.success(out, confidence, "ai assess", "OK");
  }
}
