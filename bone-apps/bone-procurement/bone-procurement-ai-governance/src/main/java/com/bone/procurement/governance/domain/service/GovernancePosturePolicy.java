package com.bone.procurement.governance.domain.service;

import com.bone.procurement.governance.domain.model.capability.DriftVerdict;
import com.bone.procurement.governance.domain.model.capability.OversightVerdict;
import com.bone.procurement.governance.domain.model.capability.PostureVerdict;
import java.util.List;
import java.util.Objects;

/**
 * 治理态势汇总策略（纯函数，无 Spring / I/O 依赖）。
 *
 * <p>规则单一真源：任一必填分项不可判定（{@code UNDETERMINED}）→ 整体不可判定；否则任一分项 actionable → 需关注；否则全清。
 * 空清单视为全清——无能力即无可疑信号，但此结论不声明模型健康。 decisionAllowed 只作展示，不参与汇总：人工有意停用/降级是正常治理态，不应制造告警。
 */
public final class GovernancePosturePolicy {

  /** 单个能力进入汇总的分项判词。 */
  public record PostureItemVerdict(
      boolean decisionAllowed, DriftVerdict drift, OversightVerdict oversight) {
    public PostureItemVerdict {
      Objects.requireNonNull(drift, "drift");
      Objects.requireNonNull(oversight, "oversight");
    }
  }

  public PostureVerdict aggregate(List<PostureItemVerdict> items) {
    Objects.requireNonNull(items, "items");
    boolean anyUndetermined = false;
    boolean anyActionable = false;
    for (PostureItemVerdict item : items) {
      if (item.drift() == DriftVerdict.UNDETERMINED
          || item.oversight() == OversightVerdict.UNDETERMINED) {
        anyUndetermined = true;
      }
      if (item.drift().isActionable() || item.oversight().isActionable()) {
        anyActionable = true;
      }
    }
    if (anyUndetermined) {
      return PostureVerdict.UNDETERMINED;
    }
    return anyActionable ? PostureVerdict.ATTENTION_REQUIRED : PostureVerdict.ALL_CLEAR;
  }
}
