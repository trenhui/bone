package com.bone.claim.domain.model.appeal;

/** 复议结论：维持原结论（UPHELD → 45） / 推翻重启审核（REOPEN → 41）。 */
public enum AppealReviewResult {
  UPHELD,
  REOPEN
}
