package com.bone.claim.application.handler;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import com.bone.claim.domain.model.decision.DecisionGraph;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 决策节点 {@code RuleCheck}（RULE 类）：核责 + 反欺诈硬闸（规则 + 视觉 E1 + 图计算 E2 综合）。
 *
 * <p>反欺诈硬闸（fraudGatePassed）为规则、视觉、图计算三路信号的逻辑与；任一命中即 {@code riskHit}。
 * 结论三路组合在应用层完成（纯布尔编排，无阈值/无金额计算——金额只走 {@code Adjust} 节点），不触碰领域规则。
 */
@Component
public class RuleCheckNodeHandler implements DecisionNodeHandler {

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

    boolean riskHit = r.riskHit() || fr.tampered() || gr.hit();
    boolean fraudGatePassed = r.fraudGatePassed() && !fr.tampered() && !gr.hit();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ruleConflict", r.ruleConflict());
    out.put("riskHit", riskHit);
    out.put("fraudGatePassed", fraudGatePassed);
    out.put("ruleConfidence", r.ruleConfidence());
    String summary =
        "conflict="
            + r.ruleConflict()
            + ",fraudGate="
            + fraudGatePassed
            + ",risk="
            + riskHit
            + ",visual="
            + fr.tampered()
            + ",graph="
            + gr.hit();
    return DecisionNodeOutcome.success(out, r.ruleConfidence(), summary, "OK");
  }
}
