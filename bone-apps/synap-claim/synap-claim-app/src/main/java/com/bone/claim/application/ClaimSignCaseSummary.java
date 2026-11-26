package com.bone.claim.application;

import java.util.List;

/** 签收批次自动建案结果摘要（新建 / 跳过 / 待人工）。 */
public record ClaimSignCaseSummary(
    int createdCount, int skippedCount, int manualCount, List<Item> items) {

  public static ClaimSignCaseSummary empty() {
    return new ClaimSignCaseSummary(0, 0, 0, List.of());
  }

  public record Item(Long itemId, String claimNo, Long caseId, String caseStatus, String result) {}
}
