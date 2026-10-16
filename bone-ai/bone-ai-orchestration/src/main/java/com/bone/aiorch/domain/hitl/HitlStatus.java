package com.bone.aiorch.domain.hitl;

/**
 * HITL 人工终审五态（AI 中台 §3.3 状态契约统一，总案/详设/DDL 三处一致）。
 *
 * <p>业界对位：AI Agent 人工监督（Human-in-the-Loop）的状态契约——AI 产出建议后停等人工裁决，杜绝自主执行不可回放。
 *
 * <ul>
 *   <li>{@link #PENDING} 待人工裁决（AI 已产出建议，停 HITL_NODE）
 *   <li>{@link #CONFIRMED} 人工确认采纳（回写业务状态机继续流转）
 *   <li>{@link #REJECTED} 人工驳回（必带 {@link ReasonType}，供规则回流）
 *   <li>{@link #MODIFIED} 人工修正（改值后采纳，必带 {@link ReasonType}）
 *   <li>{@link #ESCALATED} 升级人工全程处理（编排超时/超轮触发；与产品侧 FR-205 两端同名贯通，见总案 §3.3）
 * </ul>
 */
public enum HitlStatus {
  PENDING,
  CONFIRMED,
  REJECTED,
  MODIFIED,
  ESCALATED;

  /** 终态（不再流转）。 */
  public boolean terminal() {
    return this == CONFIRMED || this == REJECTED || this == MODIFIED || this == ESCALATED;
  }
}
