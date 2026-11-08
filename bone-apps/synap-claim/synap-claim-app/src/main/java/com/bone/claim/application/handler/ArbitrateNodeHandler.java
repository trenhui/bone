package com.bone.claim.application.handler;

import com.bone.claim.application.port.out.DecisionNodeHandler;
import com.bone.claim.domain.model.decision.DecisionGraph;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 决策节点 {@code Arbitrate}（DECISION 类）：确定性仲裁收口。
 *
 * <p>结论由 {@code DecisionTriage} 依据前序节点输出计算，本节点只做"裁决收口"动作（不新增输出键）。 二期 HITL 五态（C3）的 {@code HitlItem}
 * 回流在此锚定——本节点为确定性节点，失败即中止转人工。
 */
@Component
public class ArbitrateNodeHandler implements DecisionNodeHandler {

  @Override
  public String nodeName() {
    return DecisionGraph.ARBITRATE;
  }

  @Override
  public DecisionNodeOutcome execute(DecisionNodeContext context) {
    return DecisionNodeOutcome.success(Map.of(), null, "arbitrate", "OK");
  }
}
