package com.bone.claim.adapter.web.dto.request;

import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 报案提交请求（员工端 / 存量旁路 sync 共用）。 */
@Schema(description = "报案提交请求")
public record SubmitClaimReq(
    @Schema(description = "保单号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "保单号不能为空")
        String policyNo,
    @Schema(description = "计划ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "计划ID不能为空")
        Long planId,
    @Schema(
            description = "被保人假名化ID（服务端按 OIDC 主体计算，客户端不传明文）",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "被保人假名化ID不能为空")
        String insuredIdHash,
    @Schema(description = "险种（一期仅 OUTPATIENT）", defaultValue = "OUTPATIENT") String claimType,
    @Schema(description = "发票代码") String invoiceCode,
    @Schema(description = "发票号码") String invoiceNo,
    @Schema(
            description =
                "发票开票日期（「次」分组维度）。与出险时间不同：出险日是就医发生日，开票日是结算日。"
                    + "缺省时，配置了「同日」次限额的责任仅在可证单票时才降级处理，否则转人工")
        java.time.LocalDate invoiceDate,
    @Schema(description = "医院编码（按编码比对，不按名称）") String hospitalCode,
    @Schema(description = "病种/诊断编码（一次就诊一个主要诊断）") String diagnosisCode,
    @Schema(description = "出险时间", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "出险时间不能为空")
        Instant outInsureTime,
    @Schema(description = "批次号（报案号生成前缀，可空）") String batchNo,
    @Schema(description = "费用明细（至少一项）", requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid
        @NotNull(message = "费用明细不能为空")
        @Size(min = 1, message = "至少需要一个费用明细项")
        List<@Valid ClaimItemReq> items) {

  /** 明细入参。 */
  @Schema(description = "费用明细项")
  public record ClaimItemReq(
      @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "明细项名称不能为空")
          String itemName,
      @Schema(description = "医保项目/药品编码（目录编码匹配唯一键）") String itemCode,
      @Schema(description = "费用类别") ItemCategory category,
      @Schema(description = "数量") @DecimalMin(value = "0", message = "数量不能为负") BigDecimal quantity,
      @Schema(description = "单价") @DecimalMin(value = "0", message = "单价不能为负") BigDecimal unitPrice,
      @Schema(description = "金额", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "金额不能为空")
          @DecimalMin(value = "0", message = "金额不能为负")
          BigDecimal amount,
      @Schema(description = "就诊科室（行级「次」分组维度；一张发票可跨科室）") String department) {}
}
