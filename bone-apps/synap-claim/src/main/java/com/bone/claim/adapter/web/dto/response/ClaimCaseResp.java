package com.bone.claim.adapter.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 报案提交响应（201）。 */
@Schema(description = "报案提交响应")
public record ClaimCaseResp(
    @Schema(description = "案件ID") Long claimId,
    @Schema(description = "报案号") String claimNo,
    @Schema(description = "对外状态（两值投影：PENDING_EVIDENCE / REVIEWING）") String status) {

  public static ClaimCaseResp of(Long claimId, String claimNo, String status) {
    return new ClaimCaseResp(claimId, claimNo, status);
  }
}
