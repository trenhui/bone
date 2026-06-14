package com.bone.example.extension.medical;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.api.annotation.ExtensionDoc;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;

/**
 * 门诊理赔扩展点实现类
 *
 * <p>专门处理门诊类型的医疗保险理赔，提供符合门诊就医特点的验证和处理逻辑。 实现了对门诊就诊记录、处方信息和费用明细的验证和理赔金额计算。
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "门诊理赔扩展实现",
    tenant = "default",
    bizCode = "MEDICAL_CLAIM",
    condition = "#data.claimType == 'OUTPATIENT'",
    order = 100)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "专门处理门诊类型的医疗保险理赔，包括门诊费用验证和理赔计算",
    scenario = "适用于门诊就医后的理赔场景",
    feature = "验证门诊就诊信息，计算门诊费用的理赔金额",
    differences = "与住院理赔相比，流程更简单，理赔标准和限额不同",
    note = "提供了完整的门诊理赔处理逻辑，包括验证和计算",
    author = "测试团队",
    created = "2024-01-01")
@Slf4j
public class OutpatientClaimExtension implements MedicalClaimExtPoint {

  // 常量定义
  private static final int CLAIMS_VALIDITY_DAYS = 90; // 理赔有效期90天
  private static final BigDecimal REIMBURSEMENT_RATE = new BigDecimal("0.7"); // 门诊理赔报销比例70%

  @Override
  public ValidationResult validateClaim(final BizContext<MedicalClaimRequest> context) {
    String requestId = context.getRequestId();
    log.info("开始验证门诊理赔请求 | requestId={}", requestId);

    // 验证上下文和请求数据
    if (context == null || context.getData() == null) {
      log.warn("门诊理赔请求上下文或数据为空 | requestId={}", requestId);
      return ValidationResult.fail("INVALID_REQUEST", "理赔请求信息不完整");
    }

    final MedicalClaimRequest request = context.getData();

    // 验证必要字段
    ValidationResult validationResult = validateRequiredFields(request);
    if (!validationResult.isSuccess()) {
      log.warn(
          "门诊理赔必要字段验证失败 | requestId={} | userId={} | error={}",
          requestId,
          request.getUserId(),
          validationResult.getErrorMessage());
      return validationResult;
    }

    // 验证理赔时效性
    if (!isWithinValidityPeriod(request.getMedicalDate())) {
      log.warn(
          "门诊理赔超出有效期 | requestId={} | userId={} | medicalDate={}",
          requestId,
          request.getUserId(),
          request.getMedicalDate());
      return ValidationResult.fail("EXPIRED_CLAIM", "门诊理赔已超出90天有效期");
    }

    // 验证理赔项目
    if (request.getItems() == null || request.getItems().isEmpty()) {
      return ValidationResult.fail("EMPTY_CLAIM_ITEMS", "理赔项目不能为空");
    }

    log.info(
        "门诊理赔请求验证通过 | requestId={} | userId={} | itemCount={}",
        requestId,
        request.getUserId(),
        request.getItems().size());
    return ValidationResult.success();
  }

  @Override
  public MedicalClaimResult processClaim(final BizContext<MedicalClaimRequest> context) {
    String requestId = context.getRequestId();
    log.info("开始处理门诊理赔请求 | requestId={}", requestId);

    if (context == null || context.getData() == null) {
      throw new IllegalArgumentException("理赔请求信息不完整");
    }

    final MedicalClaimRequest request = context.getData();

    // 计算批准金额，应用门诊理赔报销比例
    BigDecimal approvedAmount = calculateApprovedAmount(request.getTotalAmount());

    // 创建理赔结果，使用Builder模式设置所有字段
    final MedicalClaimResult result =
        MedicalClaimResult.builder()
            .claimId(request.getClaimId())
            .status(MedicalClaimResult.ClaimStatus.APPROVED)
            .processingDate(LocalDateTime.now())
            .totalClaimAmount(request.getTotalAmount())
            .approvedAmount(approvedAmount)
            .rejectedAmount(request.getTotalAmount().subtract(approvedAmount))
            .processorId("SYSTEM")
            .paymentStatus("PENDING")
            .build();

    log.info(
        "门诊理赔处理完成 | requestId={} | claimId={} | totalAmount={} | approvedAmount={}",
        requestId,
        request.getClaimId(),
        request.getTotalAmount(),
        approvedAmount);
    return result;
  }

  /**
   * 验证理赔是否在有效期内
   *
   * @param medicalDate 就诊日期
   * @return 是否在有效期内
   */
  private boolean isWithinValidityPeriod(LocalDateTime medicalDate) {
    LocalDate medicalLocalDate = medicalDate.toLocalDate();
    LocalDate currentDate = LocalDate.now();
    return !medicalLocalDate.plusDays(CLAIMS_VALIDITY_DAYS).isBefore(currentDate);
  }

  /**
   * 验证必要字段
   *
   * @param request 理赔请求
   * @return 验证结果
   */
  private ValidationResult validateRequiredFields(final MedicalClaimRequest request) {
    if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
      return ValidationResult.fail("MISSING_USER_ID", "用户ID不能为空");
    }

    if (request.getMedicalDate() == null) {
      return ValidationResult.fail("MISSING_MEDICAL_DATE", "就诊日期不能为空");
    }

    if (request.getHospitalName() == null || request.getHospitalName().trim().isEmpty()) {
      return ValidationResult.fail("MISSING_HOSPITAL", "医院名称不能为空");
    }

    return ValidationResult.success();
  }

  /**
   * 计算批准的理赔金额
   *
   * @param totalAmount 总金额
   * @return 批准的理赔金额
   */
  private BigDecimal calculateApprovedAmount(final BigDecimal totalAmount) {
    return totalAmount.multiply(REIMBURSEMENT_RATE).setScale(2, java.math.RoundingMode.HALF_UP);
  }
}
