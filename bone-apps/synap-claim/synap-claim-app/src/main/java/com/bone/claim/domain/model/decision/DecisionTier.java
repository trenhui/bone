package com.bone.claim.domain.model.decision;

/**
 * 决策分档（内部分档，与对外 4 值 {@link DecisionResult} 正交）。
 *
 * <p>置信度三档（R-MAN-01）：{@code <0.7} 强制人工 / {@code 0.7–0.85} 进复核档 / {@code ≥0.85} 且无风险直出。
 * 复核档与强制人工同为"不自动通过"，但后续路由不同：复核档走"审核通过待复核"（双人复核），强制人工走人工核赔。 分档内部分析结果，不对外暴露。
 */
public enum DecisionTier {
  /** 直出（可自动通过）。 */
  AUTO,
  /** 复核档（进双人复核）。 */
  REVIEW,
  /** 强制人工。 */
  MANUAL
}
