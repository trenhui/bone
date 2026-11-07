package com.bone.aidoc.domain.model;

/**
 * 复核决策及其判据说明（ADR-0046 §四「可审计、可追溯」；NIST AI RMF 可解释性要求）。
 *
 * <p>此前 {@link ReviewDecision} 只有三值枚举，无法回答「为什么这个金额被自动放行 / 转人工」。
 * 本记录把命中的闸门、信号值与阈值快照一并带出，由编排层写入文档级审计轨迹。
 *
 * @param decision 复核路由结果
 * @param reason 判定依据（命中哪一层闸门 / 哪个阈值 / 哪条规则），不可为空
 */
public record ReviewOutcome(ReviewDecision decision, String reason) {

  public ReviewOutcome {
    reason = (reason == null || reason.isBlank()) ? "未提供判据说明" : reason;
  }

  public static ReviewOutcome autoPass(String reason) {
    return new ReviewOutcome(ReviewDecision.AUTO_PASS, reason);
  }

  public static ReviewOutcome secondary(String reason) {
    return new ReviewOutcome(ReviewDecision.SECONDARY_RECOGNIZE, reason);
  }

  public static ReviewOutcome manual(String reason) {
    return new ReviewOutcome(ReviewDecision.MANUAL_REVIEW, reason);
  }
}
