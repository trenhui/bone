package com.bone.claim.adapter.web.assembler;

import com.bone.claim.adapter.web.dto.response.DecisionTraceResp;
import com.bone.claim.domain.model.decision.DecisionTraceNode;
import java.util.List;

/**
 * 决策轨迹响应装配的唯一真源。
 *
 * <p>两个回放端点（决策执行 / 复核任务）共用同一份映射，避免「一端修了另一端没修」的契约漂移；Controller 只负责调用，不内联拷贝字段。
 */
public final class DecisionTraceAssembler {

  private DecisionTraceAssembler() {}

  public static List<DecisionTraceResp> toResp(List<DecisionTraceNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return List.of();
    }
    return nodes.stream().map(DecisionTraceResp::of).toList();
  }
}
