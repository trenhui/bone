package com.bone.claim.domain.model.settlement;

/**
 * 资金状态（{@code FundingEvent.stage}，追加式不可变，§6.4）。
 *
 * <pre>
 * SOFT_RESERVED → HARD_RESERVED → REVIEW_FREEZE → PAID
 *      │              │                │                │
 *   RELEASED       RELEASED          RELEASED       FAILED / REFUNDED
 * </pre>
 *
 * <p>金额 ≥10000 时 {@code HARD_RESERVED} 后置 {@code REVIEW_FREEZE}（双人复核门，不改已冻结金额）。 {@code
 * REVIEW_FREEZE → HARD_RESERVED} 视为同一已冻结态的复核放行，不构成逆向——§11.7「阶段单调」在该分支表述为单向。
 */
public enum FundingStage {
  SOFT_RESERVED,
  HARD_RESERVED,
  REVIEW_FREEZE,
  PAID,
  RELEASED,
  FAILED,
  REFUNDED;

  /** 已支付（终态，资金实际出账）。 */
  public boolean isPaid() {
    return this == PAID;
  }

  /** 终态：支付成功 / 释放 / 失败 / 退款。 */
  public boolean isTerminal() {
    return this == PAID || this == RELEASED || this == FAILED || this == REFUNDED;
  }

  /** 已冻结（SOFT/HARD/REVIEW_FREEZE 在额度三值模型里都计入 {@code frozen}）。 */
  public boolean isFrozen() {
    return this == SOFT_RESERVED || this == HARD_RESERVED || this == REVIEW_FREEZE;
  }

  /**
   * 可支付：<b>仅</b>已硬预占。
   *
   * <p>{@code REVIEW_FREEZE} 不可支付——它是「等第二人复核」的中间态，若允许直接出账， 双人复核就只是个装饰；放行路径只有 {@code
   * confirmReview}（REVIEW_FREEZE → HARD_RESERVED）。
   */
  public boolean isReadyToPay() {
    return this == HARD_RESERVED;
  }
}
