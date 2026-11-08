package com.bone.claim.domain.model.mapping.stp;

/** STP 五把尺（主设计 §6.9）。每把尺独立判定，任一不达标即阻断自动理算。 */
public enum StpCondition {
  ITEM_HIT_RATE("明细级命中率≥99.5%"),
  CASE_STRAIGHT_THROUGH_RATE("赔案级直通率≥95%"),
  OPEN_MISS("OPEN 未命中=0"),
  CORRECTION_EVENTS_7D("近7天无映射纠错(reopen)"),
  CONFLICT("冲突数=0（该域禁自动理算）");

  private final String requirement;

  StpCondition(String requirement) {
    this.requirement = requirement;
  }

  /** 该条件的不达标时业务后果（用于响应/告警文案）。 */
  public String requirement() {
    return requirement;
  }
}
