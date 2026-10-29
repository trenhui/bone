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
 *
 * <p><b>三态能力语义（关键安全契约）</b>：知识检索能力只有两种"可用"结论——"确认为合理"与"确认不合理"； 能力不可用是第三种状态「无法判定」，
 * <b>它既不是合理，也不是拒赔理由</b>：
 *
 * <ul>
 *   <li>能力不可用 → {@code capabilityMissing=true} + {@code
 *       capabilityReason=KNOWLEDGE_UNAVAILABLE}，由决策图统一 fail-safe 落 {@code MANUAL_REQUIRED}（转人工队列）；
 *   <li>拒赔建议（{@code rejectSuggested}）<b>只来自有依据的不利结论</b>：AI 自身给出带理由的拒赔，或知识库真实核验判定临床不合理。
 * </ul>
 *
 * <p><b>为什么不能把"无法判定"映射为拒赔</b>：拒赔要触发《保险法》24 条 3 日书面通知、五要素通知书、48h 申诉通道、AI 参与告知与监管投诉计量；
 * 转人工只是进一个队列。用"拒赔"做兜底，等于让"没接线"变成对客户的不利决定——且该结论会随决策轨迹落库， 成为对客户不利的"事实认定"。因此 fail-safe
 * 的落点必须是人工，而不是预设不利结论。
 */
@Component
public class AiAssessNodeHandler implements DecisionNodeHandler {

  /** 能力不可用受控原因码（禁自由文本，随 trace 落库供审计区分"没查"与"查出了问题"）。 */
  static final String REASON_KNOWLEDGE_UNAVAILABLE = "KNOWLEDGE_UNAVAILABLE";

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
    // 三态：available=false 即"无法判定"，clinicallyReasonable 的默认值不可被采信（既非合理也非不合理）
    boolean knowledgeAvailable = k.available();
    // 仅"能力可用且确认为不合理"才是不利结论的依据
    boolean clinicallyUnreasonable = knowledgeAvailable && !k.clinicallyReasonable();

    boolean rejectSuggested;
    BigDecimal confidence;
    AiGatewayPort.AiAssessment a = null;
    try {
      a = aiGatewayPort.assess(v);
      // 拒赔建议 = AI 自身有依据的拒赔建议 ∪ 知识库真实核验判定的临床不合理；
      // 能力不可用绝不并入本字段（否则"没接线"会被落成对客户的不利决定）
      rejectSuggested = a.rejectSuggested() || clinicallyUnreasonable;
      confidence = a.confidence();
    } catch (AiGatewayUnavailableException ex) {
      // 执行器据此节点类型为 LLM → 记 FALLBACK，confidence 回退 ruleConfidence
      rejectSuggested = clinicallyUnreasonable;
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("rejectSuggested", rejectSuggested);
      // 可解释性：显式标注 AI 能力不可用（降级纯规则），供核赔员/审计区分"AI 未评估"与"AI 评估为无风险"
      out.put("aiAvailable", false);
      putCapability(out, knowledgeAvailable);
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
    // 溯源：AI 真实参与时自报上游模型 + 提示词契约版本，供决策复算 / 算法备案 / 投诉举证
    out.put("aiModel", a == null ? "" : a.modelVersion());
    out.put("aiPromptVersion", a == null ? "" : a.promptVersion());
    // 可解释性：AI 与临床知识能力是否真实参与（false=降级/未集成，核赔员须据此提高人工审查力度）
    out.put("aiAvailable", true);
    putCapability(out, knowledgeAvailable);
    if (rejectSuggested) {
      notificationPort.alert("WARN", "AI_RISK", v, out);
    }
    return DecisionNodeOutcome.success(out, confidence, "ai assess", "OK");
  }

  /**
   * 能力可用性出站字段：{@code capabilityMissing=true} 表示"本节点无法判定"（不是否决），由决策图统一转人工。
   *
   * <p>与 {@code rejectSuggested} 严格分离：前者进人工队列，后者才是不利决定。历史上二者被合并（{@code rejectSuggested = ... ||
   * !knowledgeConfirmed}），导致"知识库未接入"被落成"系统建议拒赔"。
   */
  private static void putCapability(Map<String, Object> out, boolean knowledgeAvailable) {
    out.put("knowledgeAvailable", knowledgeAvailable);
    out.put("capabilityMissing", !knowledgeAvailable);
    if (!knowledgeAvailable) {
      out.put("capabilityReason", REASON_KNOWLEDGE_UNAVAILABLE);
    }
  }
}
