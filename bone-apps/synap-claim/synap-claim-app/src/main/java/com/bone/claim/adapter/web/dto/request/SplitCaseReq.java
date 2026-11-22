package com.bone.claim.adapter.web.dto.request;

import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 分案请求：在报案下新建一个赔案（继承母案主体/保单，明细与发票来自本请求）。 */
@Schema(description = "分案请求")
public record SplitCaseReq(
    @Schema(description = "报案号（RP 前缀）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "报案号不能为空")
        String reportNo,
    @Schema(description = "发票代码") String invoiceCode,
    @Schema(description = "发票号码") String invoiceNo,
    @Schema(description = "开票日期") LocalDate invoiceDate,
    @Schema(description = "医院编码") String hospitalCode,
    @Schema(description = "病种编码") String diagnosisCode,
    @Schema(description = "费用明细（至少一项）", requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid
        @NotNull(message = "费用明细不能为空")
        @Size(min = 1, message = "至少需要一个费用明细项")
        List<@Valid ClaimItemReq> items) {

  @Schema(description = "分案费用明细项")
  public record ClaimItemReq(
      @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "明细项名称不能为空")
          String itemName,
      @Schema(description = "医保项目/药品编码") String itemCode,
      @Schema(description = "费用类别") ItemCategory category,
      @Schema(description = "数量") @DecimalMin(value = "0") BigDecimal quantity,
      @Schema(description = "单价") @DecimalMin(value = "0") BigDecimal unitPrice,
      @Schema(description = "金额", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "金额不能为空")
          @DecimalMin(value = "0")
          BigDecimal amount,
      @Schema(description = "就诊科室") String department,
      @Schema(description = "费用类别编码") String feeCode) {}
}
