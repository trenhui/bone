package com.bone.claim.adapter.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 签收相关请求 DTO 聚合（请求形状，非领域模型）。 */
public final class ClaimSignRequests {

  private ClaimSignRequests() {}

  /** 线上自动签收回执：外部推送幂等建案签收。 */
  public record AcknowledgeSignReq(
      @Schema(description = "业务唯一标识（幂等真源）") @NotBlank String businessKey,
      @Schema(description = "推送渠道") @NotBlank String signChannel,
      @Schema(description = "案件号（可空）") String claimNo,
      @Schema(description = "赔案金额（可空）") Long claimAmount) {}

  /** 线下创建批次。 */
  public record CreateSignBatchReq(
      @Schema(description = "渠道：EXPRESS/COUNTER") @NotBlank String signChannel,
      @Schema(description = "业务唯一标识（可空）") String businessKey,
      @Schema(description = "快递公司") String expressCompany,
      @Schema(description = "快递单号") String expressNo,
      @Schema(description = "发件人") String sender,
      @Schema(description = "到件时间") Instant arriveTime) {}

  /** 导入人员名单：赔案 ID 列表。 */
  public record ImportParticipantsReq(@Schema(description = "赔案ID列表") List<Long> claimIds) {}

  /** 确认签收。 */
  public record ConfirmSignReq(
      @Schema(description = "签收意见（不少于10字）") @NotBlank @Size(min = 10, max = 256) String remark) {}
}
