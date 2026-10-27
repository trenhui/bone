package com.bone.claim.application.handler;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.domain.model.decision.DecisionGraph;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 决策节点 {@code Adjust}（RULE 类）：理算金额。
 *
 * <p>金额只经 {@link RuleEnginePort#calculatePayout}（确定性），AI 永不参与数值判定（§4.1 资金安全）。
 * 理算因子（免赔/封顶/责任比例）由端口适配层承载，一期桩回显申请金额，二期接责任配置。
 */
@Component
public class AdjustNodeHandler implements DecisionNodeHandler {

  private final ClaimContextPort claimContextPort;
  private final RuleEnginePort ruleEnginePort;

  public AdjustNodeHandler(ClaimContextPort claimContextPort, RuleEnginePort ruleEnginePort) {
    this.claimContextPort = claimContextPort;
    this.ruleEnginePort = ruleEnginePort;
  }

  @Override
  public String nodeName() {
    return DecisionGraph.ADJUST;
  }

  @Override
  public DecisionNodeOutcome execute(DecisionNodeContext context) {
    ClaimContextPort.ClaimContextView v = claimContextPort.getContext(context.claimId());
    java.math.BigDecimal amount = ruleEnginePort.calculatePayout(v);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("amount", amount);
    return DecisionNodeOutcome.success(out, null, "payout=" + amount, "OK");
  }
}
