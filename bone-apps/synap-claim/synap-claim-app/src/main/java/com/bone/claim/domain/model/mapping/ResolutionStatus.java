package com.bone.claim.domain.model.mapping;

/** 解析结局。只有 {@link #RESOLVED} 允许携带标准码，其余一律 fail-closed。 */
public enum ResolutionStatus {
  /** 命中唯一映射，且标准码经 canonicalize 后有效。 */
  RESOLVED,
  /** 没有可用映射：进未命中队列，案件转人工/待补件。 */
  MISSING,
  /** 同一保司码命中多条且消歧属性不足：不猜，转人工裁决。 */
  AMBIGUOUS,
  /** 命中的标准码已被拆分（1:N）：无单一目标，转人工。 */
  SPLIT_REQUIRES_MANUAL,
  /** 别名链成环（A→B→A）：数据错误，必须人工修。 */
  ALIAS_CYCLE;

  public boolean isResolved() {
    return this == RESOLVED;
  }
}
