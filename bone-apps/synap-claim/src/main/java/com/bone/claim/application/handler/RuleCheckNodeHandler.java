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
 * <p><b>fail-closed 安全契约（R-FRAUD，§6.3）</b>：规则 / 视觉 E1 / 图计算 E2 三路都必须<b>真实执行过</b>（各自 {@code
 * available=true}）才可能宣称闸通过；能力未集成时其结果 {@code available=false}——<b>不可用绝不等同于无冲突/无风险</b>。
 * 任一路缺失即判定闸不过（{@code fraudGatePassed=false}）， 决策图据此 {@code FRAUD_GATE_NOT_PASSED → MANUAL_REQUIRED}
 * 升级人工，绝不静默放行。 这是对此前"本地桩恒返回 tampered=false/hit=false 让反欺诈闸恒为真"假绿缺陷的修正； 规则桩的 {@code available=false}
 * 属同一契约（否则「无冲突」会被误读为已核责结论）。
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

    // 三路信号（规则 / 视觉 E1 / 图计算 E2）是否都"真实执行过"（本地桩 available=false）
    boolean ruleAvailable = r.available();
    boolean visualAvailable = fr.available();
    boolean graphAvailable = gr.available();

    // riskHit：仅在对应能力真实执行过时，才采信其 tampered/hit；否则不可据此宣称"无风险"
    boolean riskHit =
        (ruleAvailable && r.riskHit())
            || (visualAvailable && fr.tampered())
            || (graphAvailable && gr.hit());

    // 硬闸（逻辑与）：规则核责 + 视觉 E1 + 图计算 E2 三路任一缺失 ⇒ 无法确认通过 ⇒ fail-closed（绝不放行）。
    // 规则这一路与 E1/E2 同权：核责规则没跑过时，"无冲突/验真通过"只是默认值，据此自动放款等于"没查就付"。
    boolean capabilitiesPresent = ruleAvailable && visualAvailable && graphAvailable;
    boolean fraudGatePassed =
        capabilitiesPresent ? (r.fraudGatePassed() && !fr.tampered() && !gr.hit()) : false;

    if (!capabilitiesPresent) {
      log.warn(
          "核责/反欺诈能力缺失 → 决策图 fail-closed 升级人工（ruleAvailable={}, visualAvailable={}, "
              + "graphAvailable={}）；自动通过在此状态下被禁用，直至 C2/E1/E2 接入",
          ruleAvailable,
          visualAvailable,
          graphAvailable);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ruleConflict", r.ruleConflict());
    out.put("riskHit", riskHit);
    out.put("fraudGatePassed", fraudGatePassed);
    out.put("ruleConfidence", r.ruleConfidence());
    // 可解释性：把"闸为什么没过"落成受控原因码（而非只给布尔）——核赔员/审计据此区分
    // 「能力未集成（无法确认安全）」与「真实命中风险」，避免把能力缺失误读为已核验无风险。
    out.put("ruleAvailable", ruleAvailable);
    out.put("visualAvailable", visualAvailable);
    out.put("graphAvailable", graphAvailable);
    // 规则未真实执行时，禁止把默认值当结论：显式标注原因，供审计/核赔员识别"该结论未经核责"
    out.put("ruleUnavailableReason", ruleAvailable ? "" : "RULE_ENGINE_STUB");
    out.put(
        "fraudGateReason",
        fraudGateReason(
            ruleAvailable, visualAvailable, graphAvailable, r.fraudGatePassed(), fr, gr));
    // 能力缺失（无法判定）与"真实命中风险"分属两种安全态：前者转人工，后者转人工 + 风控复核。
    // 用受控原因码区分，禁止把"没查"写成"查出了问题"。
    out.put("capabilityMissing", !capabilitiesPresent);
    if (!capabilitiesPresent) {
      out.put("capabilityReason", capabilityReason(ruleAvailable, visualAvailable, graphAvailable));
    }
    String summary =
        "conflict="
            + r.ruleConflict()
            + ",fraudGate="
            + fraudGatePassed
            + ",risk="
            + riskHit
            + ",ruleAvail="
            + ruleAvailable
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
   * 硬闸未过的受控原因码（可解释性）：{@code RULE_ENGINE_UNAVAILABLE} 表示核责规则未真实执行；{@code
   * ANTI_FRAUD_CAPABILITY_MISSING} 表示视觉 E1 / 图计算 E2 未集成；{@code RISK_HIT} 表示真实命中风险；{@code
   * VERIFY_NOT_PASSED} 表示验真未过；{@code PASSED} 表示三路真实执行且均通过。
   *
   * <p><b>口径说明</b>：规则路的可用性（{@code ruleAvailable}）<b>已并入</b>本硬闸（2026-10-29）。此前按 ADR-002 一期豁免只做标注，
   * 前提是"E1/E2 未接入 ⇒ 闸本就恒不通过"，故并入不改变任何可达结论；但一旦 E1/E2 先于 C2 接入，不带规则路的闸就会放行"没核责"的案件—— 豁免的前提届时自动失效。
   * 现按"不可用绝不等同于无冲突"原则一次收敛，代价是 {@code AUTO_APPROVE} 以 C2 真实交付为前置。
   */
  private static String fraudGateReason(
      boolean ruleAvailable,
      boolean visualAvailable,
      boolean graphAvailable,
      boolean ruleVerifyPassed,
      VisualForensicsPort.ForensicsResult fr,
      GraphFraudPort.FraudResult gr) {
    if (!ruleAvailable) {
      return "RULE_ENGINE_UNAVAILABLE";
    }
    if (!visualAvailable || !graphAvailable) {
      return "ANTI_FRAUD_CAPABILITY_MISSING";
    }
    if (fr.tampered() || gr.hit()) {
      return "RISK_HIT";
    }
    if (!ruleVerifyPassed) {
      return "VERIFY_NOT_PASSED";
    }
    return "PASSED";
  }

  /**
   * 能力缺失（无法判定）的受控原因码——与 {@code fraudGateReason} 的 {@code RISK_HIT} 严格区分：
   * 前者是"没查"，后者是"查出了问题"，两者的人工处置路径不同（补能力 vs 风控复核）。
   */
  private static String capabilityReason(
      boolean ruleAvailable, boolean visualAvailable, boolean graphAvailable) {
    StringBuilder sb = new StringBuilder("CAPABILITY_MISSING");
    if (!ruleAvailable) {
      sb.append(":RULE_ENGINE");
    }
    if (!visualAvailable) {
      sb.append(":VISUAL_E1");
    }
    if (!graphAvailable) {
      sb.append(":GRAPH_E2");
    }
    return sb.toString();
  }
}
