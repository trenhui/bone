package com.bone.claim.domain.model.mapping.stp;

import java.util.List;

/**
 * STP 闸门判定结果。{@code allowAuto=false} 表示对应 (保司×域) 不得自动理算，赔案须转人工。
 *
 * <p>条件明细随结果一并返回，前端可直接渲染「为什么这单不能直通」，而不是只给一个布尔黑盒。
 */
public record StpGateVerdict(boolean allowAuto, List<ConditionResult> conditions) {

  /** 单条件判定明细。 */
  public record ConditionResult(StpCondition condition, boolean passed, String detail) {}

  public static StpGateVerdict allow(List<ConditionResult> conditions) {
    return new StpGateVerdict(true, conditions);
  }

  public static StpGateVerdict block(List<ConditionResult> conditions) {
    return new StpGateVerdict(false, conditions);
  }
}
