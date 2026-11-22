package com.bone.claim.adapter.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 建案响应（201）。
 *
 * <p><b>两个编号口径不同</b>：{@code claimNo} 是赔案号（{@code CL…}），{@code reportNo} 是报案号（{@code RP…}）。 1:1
 * 时代只有前者，前端把它当报案号展示——拆分后必须同时给出，否则客服无法从响应里拿到「这次事故」的锚点。 新增字段不破坏既有调用方（旧客户端忽略未知字段）。
 */
@Schema(description = "建案响应")
public record ClaimCaseResp(
    @Schema(description = "案件ID") Long claimId,
    @Schema(description = "赔案号（CL 前缀）") String claimNo,
    @Schema(description = "报案号（RP 前缀；同一报案下多个赔案共享）") String reportNo,
    @Schema(description = "对外状态（两值投影：PENDING_EVIDENCE / REVIEWING）") String status) {

  public static ClaimCaseResp of(Long claimId, String claimNo, String reportNo, String status) {
    return new ClaimCaseResp(claimId, claimNo, reportNo, status);
  }
}
