package com.bone.claim.adapter.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** 配置面请求体（保单 / 计划 / 险种）。 */
public final class ClaimConfigRequests {

  private ClaimConfigRequests() {}

  /** 新建保单。 */
  @Schema(description = "新建保单请求")
  public record CreatePolicyReq(
      @Schema(description = "保单号", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "保单号不能为空")
          String policyNo,
      @Schema(description = "保单名称") String policyName,
      @Schema(description = "保险公司") String insurerName,
      @Schema(description = "保险分公司") String branchName,
      @Schema(description = "投保公司") String insureName,
      @Schema(description = "业务主体码") String bizIdentityCode,
      @Schema(description = "产品类型") String productType,
      @Schema(description = "生效起", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "生效起不能为空")
          Instant effectiveStart,
      @Schema(description = "生效止", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "生效止不能为空")
          Instant effectiveEnd,
      @Schema(description = "等待期（天）") @Min(value = 0, message = "等待期不能为负") Integer waitingPeriod,
      @Schema(description = "保单版本") String policyVersion,
      @Schema(description = "被保人清单（假名化ID数组；不存明文证件）") String insuredSnapshot) {}

  /** 新建计划。 */
  @Schema(description = "新建计划请求")
  public record CreatePlanReq(
      @Schema(description = "所属保单号", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "保单号不能为空")
          String policyNo,
      @Schema(description = "计划编码", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "计划编码不能为空")
          String planCode,
      @Schema(description = "计划名称") String planName,
      @Schema(description = "计划额度上限（0=不限）") @DecimalMin(value = "0", message = "计划额度不能为负")
          BigDecimal planLimit,
      @Schema(description = "等待期（天）") @Min(value = 0, message = "等待期不能为负") Integer waitingPeriod,
      @Schema(description = "计划版本") String versionNo) {}

  /** 新建险种。 */
  @Schema(description = "新建险种请求")
  public record CreateCoverageReq(
      @Schema(description = "所属计划ID", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "计划ID不能为空")
          Long planId,
      @Schema(description = "险种编码", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "险种编码不能为空")
          String coverageCode,
      @Schema(description = "险种名称") String coverageName,
      @Schema(description = "险种额度上限（0=不限）") @DecimalMin(value = "0", message = "险种额度不能为负")
          BigDecimal coverageLimit,
      @Schema(
              description = "责任形式",
              allowableValues = {"REIMBURSEMENT", "FIXED_AMOUNT", "ALLOWANCE"})
          String liabilityType) {}

  /** 编辑保单（不改动保单号与规则开关）。 */
  @Schema(description = "编辑保单请求")
  public record UpdatePolicyReq(
      @Schema(description = "保单名称") String policyName,
      @Schema(description = "保险公司") String insurerName,
      @Schema(description = "保险分公司") String branchName,
      @Schema(description = "投保公司") String insureName,
      @Schema(description = "业务主体码") String bizIdentityCode,
      @Schema(description = "产品类型") String productType,
      @Schema(description = "生效起", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "生效起不能为空")
          Instant effectiveStart,
      @Schema(description = "生效止", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "生效止不能为空")
          Instant effectiveEnd,
      @Schema(description = "等待期（天）") @Min(value = 0, message = "等待期不能为负") Integer waitingPeriod,
      @Schema(description = "保单版本") String policyVersion,
      @Schema(description = "被保人清单（假名化ID数组；不存明文证件）") String insuredSnapshot,
      @Schema(description = "乐观锁版本号（来自列表/详情，提交时原样带回用于并发冲突检测）") Long version) {}

  /** 编辑计划（不改动计划编码与状态）。 */
  @Schema(description = "编辑计划请求")
  public record UpdatePlanReq(
      @Schema(description = "计划名称") String planName,
      @Schema(description = "计划额度上限（0=不限）") @DecimalMin(value = "0", message = "计划额度不能为负")
          BigDecimal planLimit,
      @Schema(description = "等待期（天）") @Min(value = 0, message = "等待期不能为负") Integer waitingPeriod,
      @Schema(description = "计划版本") String versionNo,
      @Schema(description = "乐观锁版本号（来自列表/详情，提交时原样带回用于并发冲突检测）") Long version) {}

  /** 编辑险种（不改动险种编码与状态）。 */
  @Schema(description = "编辑险种请求")
  public record UpdateCoverageReq(
      @Schema(description = "险种名称") String coverageName,
      @Schema(description = "险种额度上限（0=不限）") @DecimalMin(value = "0", message = "险种额度不能为负")
          BigDecimal coverageLimit,
      @Schema(
              description = "责任形式",
              allowableValues = {"REIMBURSEMENT", "FIXED_AMOUNT", "ALLOWANCE"})
          String liabilityType,
      @Schema(description = "乐观锁版本号（来自列表/详情，提交时原样带回用于并发冲突检测）") Long version) {}

  /** 开户（初始化个人额度）。 */
  @Schema(description = "额度开户请求")
  public record OpenQuotaReq(
      @Schema(description = "被保人假名化ID", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "被保人标识不能为空")
          String insuredIdHash,
      @Schema(description = "保单号", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "保单号不能为空")
          String policyNo,
      @Schema(description = "计划ID", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "计划ID不能为空")
          Long planId,
      @Schema(description = "额度年度（缺省取当前自然年）") Integer quotaYear,
      @Schema(description = "额度维度", defaultValue = "PERSONAL") @Size(max = 32, message = "额度维度过长")
          String quotaType,
      @Schema(description = "责任UUID（LIABILITY_* 维度必填）") @Size(max = 64, message = "责任UUID过长")
          String liabilityUuid,
      @Schema(description = "初始额度", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "初始额度不能为空")
          @DecimalMin(value = "0", message = "初始额度不能为负")
          BigDecimal quotaInitial,
      @Schema(description = "变更说明") @Size(max = 500, message = "变更说明过长") String remark) {}

  /** 调额。 */
  @Schema(description = "额度调额请求")
  public record AdjustQuotaReq(
      @Schema(description = "新初始额度", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "初始额度不能为空")
          @DecimalMin(value = "0", message = "初始额度不能为负")
          BigDecimal newInitial,
      @Schema(description = "变更说明") @Size(max = 500, message = "变更说明过长") String remark,
      @Schema(description = "乐观锁版本号（来自账户列表，提交时原样带回用于并发冲突检测）") Long version) {}

  /** 减人/恢复。 */
  @Schema(description = "额度减人/恢复请求")
  public record InsuredStateReq(
      @Schema(description = "变更说明") @Size(max = 500, message = "变更说明过长") String remark,
      @Schema(description = "乐观锁版本号（来自账户列表，提交时原样带回用于并发冲突检测）") Long version) {}
}
