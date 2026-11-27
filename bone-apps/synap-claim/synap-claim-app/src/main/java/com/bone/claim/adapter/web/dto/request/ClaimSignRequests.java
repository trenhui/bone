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
      @Schema(description = "赔案金额（可空）") Long claimAmount,
      @Schema(description = "随回执携带的建案要素，缺省只建签收批次") List<SignCaseElementReq> caseElements) {}

  /** 线上自动签收随回执携带的建案要素（实名明文，仅限受控环境）。 */
  public record SignCaseElementReq(
      String claimNo,
      String policyNo,
      Long planId,
      String insuredName,
      String insuredIdNo,
      Instant outInsureTime,
      String claimType,
      String sourceChannel) {}

  /** 线下创建批次。 */
  public record CreateSignBatchReq(
      @Schema(description = "渠道：EXPRESS/COUNTER") @NotBlank String signChannel,
      @Schema(description = "业务唯一标识（可空）") String businessKey,
      @Schema(description = "快递公司") String expressCompany,
      @Schema(description = "快递单号") String expressNo,
      @Schema(description = "发件人") String sender,
      @Schema(description = "到件时间") Instant arriveTime,
      @Schema(description = "保险公司") String insuranceCompany,
      @Schema(description = "保险分公司") String insuranceSubsidiary,
      @Schema(description = "投保公司") String insuringAgency,
      @Schema(description = "紧急程度") String emergency) {}

  /** 导入人员名单：赔案 ID 列表。 */
  public record ImportParticipantsReq(@Schema(description = "赔案ID列表") List<Long> claimIds) {}

  /** 确认签收。 */
  public record ConfirmSignReq(
      @Schema(description = "签收意见（不少于10字）") @NotBlank @Size(min = 10, max = 256) String remark) {}
}
