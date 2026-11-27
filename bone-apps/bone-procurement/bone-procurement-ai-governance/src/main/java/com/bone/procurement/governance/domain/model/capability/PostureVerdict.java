package com.bone.procurement.governance.domain.model.capability;

/**
 * 治理态势汇总判词：由各能力分项判词确定性聚合而来。
 *
 * <p>刻意采用三态：不可判定优先于需关注，需关注优先于全清；任何判词都不声明模型效果达标。 "全清"只表示当前可观测分项无 actionable 信号，不代表模型健康。
 */
public enum PostureVerdict {
  /** 至少一个必填分项不可判定或缺失；不可被当作通过。 */
  UNDETERMINED,
  /** 所有分项可判定且无 actionable 信号。 */
  ALL_CLEAR,
  /** 至少一个分项需要 owner 复核。 */
  ATTENTION_REQUIRED;

  /** 仅需关注态要求 owner 采取动作；不可判定态不可被误当作可操作告警。 */
  public boolean isActionable() {
    return this == ATTENTION_REQUIRED;
  }
}
