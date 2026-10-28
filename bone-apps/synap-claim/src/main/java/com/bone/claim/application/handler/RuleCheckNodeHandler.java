package com.bone.claim.application.handler;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import com.bone.claim.domain.model.decision.DecisionGraph;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 决策节点 {@code RuleCheck}（RULE 类）：核责 + 反欺诈硬闸（规则 + 视觉 E1 + 图计算 E2 综合）。
 *
 * <p>反欺诈硬闸（fraudGatePassed）为规则、视觉、图计算三路信号的逻辑与；任一命中即 {@code riskHit}。
 * 结论三路组合在应用层完成（纯布尔编排，无阈值/无金额计算——金额只走 {@code Adjust} 节点），不触碰领域规则。
 *
 * <p><b>fail-closed 安全契约（R-FRAUD，§6.3）</b>：视觉 E1 / 图计算 E2 属增强能力，能力未集成时其结果 {@code
 * available=false}——<b>不可用绝不等同于无欺诈</b>。 三路逻辑与必须在"三路都已真实执行"的前提下才成立；任一增强能力缺失即判定闸不过（{@code
 * fraudGatePassed=false}）， 决策图据此 {@code FRAUD_GATE_NOT_PASSED → MANUAL_REQUIRED} 升级人工，绝不静默放行。
 * 这是对此前"本地桩恒返回 tampered=false/hit=false 让反欺诈闸恒为真"假绿缺陷的修正。
 */
@Component
public class RuleCheckNodeHandler implements DecisionNodeHandler {

  private static final Logger log = LoggerFactory.getLogger(RuleCheckNodeHandler.class);

  private final ClaimContextPort claimContextPort;
  private final RuleEnginePort ruleEnginePort;
  private final VisualForensicsPort visualForensicsPort;
  private final GraphFraudPort graphFraudPort;

  public RuleCheckNodeHandler(
      ClaimContextPort claimContextPort,
      RuleEnginePort ruleEnginePort,
      VisualForensicsPort visualForensicsPort,
      GraphFraudPort graphFraudPort) {
    this.claimContextPort = claimContextPort;
    this.ruleEnginePort = ruleEnginePort;
    this.visualForensicsPort = visualForensicsPort;
    this.graphFraudPort = graphFraudPort;
  }

  @Override
  public String nodeName() {
    return DecisionGraph.RULE_CHECK;
  }

  @Override
  public DecisionNodeOutcome execute(DecisionNodeContext context) {
    ClaimContextPort.ClaimContextView v = claimContextPort.getContext(context.claimId());
    RuleEnginePort.RuleCheckResult r = ruleEnginePort.check(v);
    VisualForensicsPort.ForensicsResult fr = visualForensicsPort.inspect(v);
    GraphFraudPort.FraudResult gr = graphFraudPort.screen(v);

    // 增强反欺诈能力是否真实执行过（本地桩 available=false）
    boolean visualAvailable = fr.available();
    boolean graphAvailable = gr.available();

    // riskHit：仅在对应能力真实执行过时，才采信其 tampered/hit；否则不可据此宣称"无风险"
    boolean riskHit =
        r.riskHit() || (visualAvailable && fr.tampered()) || (graphAvailable && gr.hit());

    // 反欺诈硬闸（三路逻辑与）：任一增强能力缺失 ⇒ 无法确认通过 ⇒ fail-closed（绝不放行）
    boolean fraudGatePassed =
        (visualAvailable && graphAvailable)
            ? (r.fraudGatePassed() && !fr.tampered() && !gr.hit())
            : false;

    if (!visualAvailable || !graphAvailable) {
      log.warn(
          "反欺诈增强能力缺失 → 决策图 fail-closed 升级人工（visualAvailable={}, graphAvailable={}）；"
              + "自动通过在此状态下被禁用，直至 E1/E2 接入",
          visualAvailable,
          graphAvailable);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ruleConflict", r.ruleConflict());
    out.put("riskHit", riskHit);
    out.put("fraudGatePassed", fraudGatePassed);
    out.put("ruleConfidence", r.ruleConfidence());
    // 可解释性：把"闸为什么没过"落成受控原因码（而非只给布尔）——核赔员/审计据此区分
    // 「增强能力未集成（无法确认安全）」与「真实命中风险」，避免把能力缺失误读为已核验无风险。
    out.put("visualAvailable", visualAvailable);
    out.put("graphAvailable", graphAvailable);
    out.put("fraudGateReason", fraudGateReason(visualAvailable, graphAvailable, r, fr, gr));
    String summary =
        "conflict="
            + r.ruleConflict()
            + ",fraudGate="
            + fraudGatePassed
            + ",risk="
            + riskHit
            + ",visualAvail="
            + visualAvailable
            + ",visual="
            + fr.tampered()
            + ",graphAvail="
            + graphAvailable
            + ",graph="
            + gr.hit();
    return DecisionNodeOutcome.success(out, r.ruleConfidence(), summary, "OK");
  }

  /**
   * 反欺诈硬闸未过的受控原因码（可解释性）：{@code ANTI_FRAUD_CAPABILITY_MISSING} 表示视觉 E1 / 图计算 E2 未集成、
   * 无法确认安全（fail-closed）；{@code RISK_HIT} 表示真实命中风险；{@code VERIFY_NOT_PASSED} 表示验真未过；{@code PASSED}
   * 表示三路真实执行且均通过。
   */
  private static String fraudGateReason(
      boolean visualAvailable,
      boolean graphAvailable,
      RuleEnginePort.RuleCheckResult r,
      VisualForensicsPort.ForensicsResult fr,
      GraphFraudPort.FraudResult gr) {
    if (!visualAvailable || !graphAvailable) {
      return "ANTI_FRAUD_CAPABILITY_MISSING";
    }
    if (r.riskHit() || fr.tampered() || gr.hit()) {
      return "RISK_HIT";
    }
    if (!r.fraudGatePassed()) {
      return "VERIFY_NOT_PASSED";
    }
    return "PASSED";
  }
}
