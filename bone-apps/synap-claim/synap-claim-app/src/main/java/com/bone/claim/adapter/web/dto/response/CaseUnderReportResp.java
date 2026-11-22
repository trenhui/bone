package com.bone.claim.adapter.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 报案下赔案列表行（1:N 的"多"）。 */
@Schema(description = "报案下赔案")
public record CaseUnderReportResp(
    @Schema(description = "赔案ID（下钻用）") Long claimId,
    @Schema(description = "赔案号（CL 前缀）") String claimNo,
    @Schema(description = "内部状态码") String status,
    @Schema(description = "对外状态两值投影") String externalStatus,
    @Schema(description = "申请金额") BigDecimal claimAmount) {}
