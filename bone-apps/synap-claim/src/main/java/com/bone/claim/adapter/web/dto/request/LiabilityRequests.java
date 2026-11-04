package com.bone.claim.adapter.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 责任配置请求体。
 *
 * <p>金额类字段统一用 {@code String} 承载：前端表单与批量导入都产出字符串，由应用服务 {@code toDecimal} 解析并兜底为 0，避免 JSON
 * 数值精度与空值歧义。空字段在编辑时表示"沿用原值"。
 */
public final class LiabilityRequests {

  private LiabilityRequests() {}

  /** 新建责任。 */
  @Schema(description = "新建责任请求")
  public record CreateLiabilityReq(
      @Schema(description = "所属计划ID", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "计划ID不能为空")
          Long planId,
      @Schema(description = "所属保障/险种ID（四级建模：保单→方案→保障→责任）") Long coverageId,
      @Schema(description = "责任编码(R01…)", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "责任编码不能为空")
          String liabilityCode,
      @Schema(description = "责任类型 REIMBURSEMENT/FIXED_AMOUNT/ALLOWANCE") String liabilityType,
      @Schema(description = "赔付比例(百分数,如90)") String payPercent,
      @Schema(description = "年度免赔额") String liabilityDeduct,
      @Schema(description = "责任额度上限") String liabilityLimit,
      @Schema(description = "发票级免赔") String invoiceDeduct,
      @Schema(description = "理算级免赔") String adjustDeduct,
      @Schema(description = "日赔付限额(0=不启用)") String dailyLimit,
      @Schema(description = "日窗口口径 NONE/SAME_DAY_INVOICE/SAME_DAY_CLAIM") String dailyLimitBasis,
      @Schema(description = "适用限定方式 NONE/WHITELIST/BLACKLIST") String limitMode,
      @Schema(description = "限定医院编码名单(分隔串)") String limitHospitalCodes,
      @Schema(description = "限定病种编码名单(分隔串)") String limitDiagnosisCodes,
      @Schema(description = "目录准入策略 NONE/MANDATORY") String catalogPolicy,
      @Schema(description = "目录编码(如 NATIONAL_2024)") String catalogCode,
      @Schema(description = "是否仅赔社保内(1是0否)") @Min(0) @Max(1) Integer socialOnly,
      @Schema(description = "等待期(天)") @Min(value = 0, message = "等待期不能为负") Integer waitingPeriod,
      @Schema(description = "共享保额共保代码(空=独立责任，不共享)") String sharedCode,
      @Schema(description = "先赔后赔前序责任UUID(空=无)") String priorLiabilityUuid,
      @Schema(description = "初始状态 DRAFT/ACTIVE/RETIRED") String status) {}

  /** 编辑责任（空字段沿用原值；ACTIVE 责任将生成新版本）。 */
  @Schema(description = "编辑责任请求")
  public record UpdateLiabilityReq(
      String liabilityCode,
      Long coverageId,
      String liabilityType,
      String payPercent,
      String liabilityDeduct,
      String liabilityLimit,
      String invoiceDeduct,
      String adjustDeduct,
      String dailyLimit,
      String dailyLimitBasis,
      String limitMode,
      String limitHospitalCodes,
      String limitDiagnosisCodes,
      String catalogPolicy,
      String catalogCode,
      @Min(0) @Max(1) Integer socialOnly,
      Integer waitingPeriod,
      @Schema(description = "共享保额共保代码(空=独立责任，不共享)") String sharedCode,
      @Schema(description = "先赔后赔前序责任UUID(空=无)") String priorLiabilityUuid,
      @Schema(
              description =
                  "解除保障归属（把责任退回方案级）。JSON 里 coverageId 传 null 与\"不传\"无法区分，"
                      + "故用显式标志位；为 true 时忽略 coverageId 并清空归属。",
              defaultValue = "false")
          Boolean detachCoverage) {}

  /** 费用类别规则行（责任费用规则抽屉整表提交，金额 String 承载）。 */
  @Schema(description = "责任费用类别规则行")
  public record FeeRuleItem(
      @Schema(description = "费用类别编码（须存在于基础目录库）", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "费用类别不能为空")
          String feeCode,
      @Schema(description = "赔付比例(百分数, 0-100)") String payPercent,
      @Schema(description = "该类别单次限额(0=不限)") String limitAmount,
      @Schema(description = "仅赔社保内(1是0否)") @Min(0) @Max(1) Integer socialOnly) {}

  /** 责任费用规则全量保存请求（整表替换）。 */
  @Schema(description = "责任费用类别规则保存请求")
  public record SaveFeeRulesReq(
      @Schema(description = "规则行（空数组=清空）") java.util.List<FeeRuleItem> items) {}
}
