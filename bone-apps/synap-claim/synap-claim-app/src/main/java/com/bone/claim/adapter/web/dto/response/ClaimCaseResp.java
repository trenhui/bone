package com.bone.claim.adapter.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 报案提交响应（201）。1:N 后同时返回赔案号与报案号。 */
@Schema(description = "报案提交响应")
public record ClaimCaseResp(
    @Schema(description = "赔案ID") Long claimId,
    @Schema(description = "赔案号（CL 前缀，一笔赔付一个号）") String claimNo,
    @Schema(description = "对外状态（两值投影：PENDING_EVIDENCE / REVIEWING）") String status,
    @Schema(description = "报案ID（1:N 的“一”）") Long reportId,
    @Schema(description = "报案号（RP 前缀，一次事故告知一个号）") String reportNo) {

  /** 兼容历史三参调用（单测/旁路）：无报案归属信息时双号为 null。 */
  public static ClaimCaseResp of(Long claimId, String claimNo, String status) {
    return new ClaimCaseResp(claimId, claimNo, status, null, null);
  }

  public static ClaimCaseResp of(
      Long claimId, String claimNo, String status, Long reportId, String reportNo) {
    return new ClaimCaseResp(claimId, claimNo, status, reportId, reportNo);
  }
}
