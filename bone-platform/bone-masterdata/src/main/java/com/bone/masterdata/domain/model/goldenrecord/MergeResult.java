package com.bone.masterdata.domain.model.goldenrecord;

/** 合并裁决输出：只有 APPLIED 允许写入 golden record 并进入分发链路。 */
public final class MergeResult {

  private final MergeOutcome outcome;
  private final GoldenRecord golden;
  private final MergeAudit audit;
  private final String reason;

  private MergeResult(MergeOutcome outcome, GoldenRecord golden, MergeAudit audit, String reason) {
    this.outcome = outcome;
    this.golden = golden;
    this.audit = audit;
    this.reason = reason;
  }

  public static MergeResult applied(GoldenRecord golden, MergeAudit audit) {
    return new MergeResult(MergeOutcome.APPLIED, golden, audit, null);
  }

  public static MergeResult manualReview(String reason) {
    return new MergeResult(MergeOutcome.MANUAL_REVIEW_REQUIRED, null, null, reason);
  }

  public static MergeResult rejected(String reason) {
    return new MergeResult(MergeOutcome.REJECTED, null, null, reason);
  }

  public boolean isApplied() {
    return outcome == MergeOutcome.APPLIED;
  }

  public MergeOutcome getOutcome() {
    return outcome;
  }

  public GoldenRecord getGolden() {
    return golden;
  }

  public MergeAudit getAudit() {
    return audit;
  }

  public String getReason() {
    return reason;
  }
}
