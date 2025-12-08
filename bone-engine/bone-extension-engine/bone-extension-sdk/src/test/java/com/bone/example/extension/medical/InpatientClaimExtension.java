package com.bone.example.extension.medical;

import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.api.annotation.ExtensionDoc;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 住院理赔扩展点实现类
 * <p>
 * 专门处理住院类型的医疗保险理赔场景，提供完整的验证和处理逻辑。
 * 实现了对住院期间各项费用的验证、计算和理赔金额确定。
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "住院理赔扩展实现",
    description = "处理住院类型的医疗保险理赔请求",
    tenant= "default",
    bizCode = "MEDICAL_CLAIM",
    scenario = "INPATIENT_CLAIM",
    condition = "#data.claimType == T(com.bone.example.extension.medical.MedicalClaimRequest.ClaimType).INPATIENT",
    order = 110,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "专门处理住院类型的医疗保险理赔，包括住院费用验证和理赔计算",
    scenario = "适用于住院就医后的理赔场景",
    feature = "验证住院就诊信息，计算住院期间所有费用的理赔金额",
    differences = "与门诊理赔相比，需要验证更多住院相关信息，理赔标准和限额不同",
    note = "提供了完整的住院理赔处理逻辑，包括参数验证和理赔金额计算",
    author = "测试团队",
    created = "2024-01-01"
)
@Slf4j
public class InpatientClaimExtension implements MedicalClaimExtPoint {
    
    // 常量定义
    private static final int MIN_HOSPITAL_STAY_DAYS = 1;       // 最小住院天数
    private static final int MAX_HOSPITAL_STAY_DAYS = 365;     // 最大住院天数
    private static final BigDecimal DAILY_COST_LIMIT = new BigDecimal("5000"); // 每日费用限额
    private static final BigDecimal REIMBURSEMENT_RATE = new BigDecimal("0.8"); // 住院理赔报销比例80%
    
    /**
     * 验证住院理赔请求的合法性
     * <p>
     * 执行严格的参数验证，确保上下文和请求数据的完整性和有效性。
     * 对住院特定信息进行验证，包括入院日期、出院日期、住院天数等。
     * 
     * @param context 业务上下文，包含理赔请求信息
     * @return 验证结果，成功或包含详细错误信息
     */
    @Override
    public ValidationResult validateClaim(final BizContext<MedicalClaimRequest> context) {
        String requestId = context.getRequestId();
        log.info("开始验证住院理赔请求 | requestId={}", requestId);
        
        // 执行参数验证
        validateClaimContext(context);
        
        final MedicalClaimRequest request = context.getData();
        validateClaimRequest(request);
        
        // 验证理赔类型
        if (request.getClaimType() != MedicalClaimRequest.ClaimType.INPATIENT) {
            log.warn("理赔类型不匹配 | requestId={} | claimId={} | expected=INPATIENT | actual={}", 
                    requestId, request.getClaimId(), request.getClaimType());
            return ValidationResult.fail("INVALID_CLAIM_TYPE", "理赔类型不匹配，该扩展点仅支持住院理赔");
        }
        
        // 验证住院特定信息
        ValidationResult validationResult = validateInpatientInfo(request);
        if (!validationResult.isSuccess()) {
            log.warn("住院理赔特定信息验证失败 | requestId={} | claimId={} | userId={} | error={}", 
                    requestId, request.getClaimId(), request.getUserId(), validationResult.getErrorMessage());
            return validationResult;
        }
        
        log.info("住院理赔请求验证通过 | requestId={} | claimId={} | userId={} | hospitalStayDays={}", 
                requestId, request.getClaimId(), request.getUserId(), calculateHospitalStayDays(request));
        return ValidationResult.success();
    }
    
    /**
     * 处理住院理赔请求
     * <p>
     * 在验证通过的基础上，进行理赔金额计算和结果生成。
     * 实现住院费用的理赔计算逻辑，考虑住院天数、费用类型等因素。
     * 
     * @param context 业务上下文，包含已验证的理赔请求信息
     * @return 理赔处理结果，包含理赔金额和相关信息
     */
    @Override
    public MedicalClaimResult processClaim(final BizContext<MedicalClaimRequest> context) {
        String requestId = context.getRequestId();
        log.info("开始处理住院理赔请求 | requestId={}", requestId);
        
        // 再次验证参数，确保数据有效性
        validateClaimContext(context);
        final MedicalClaimRequest request = context.getData();
        validateClaimRequest(request);
        
        // 计算住院天数
        long hospitalStayDays = calculateHospitalStayDays(request);
        
        // 计算每日平均费用
        BigDecimal dailyAverageCost = calculateDailyAverageCost(request.getTotalAmount(), hospitalStayDays);
        
        // 根据住院天数和费用计算理赔金额
        BigDecimal approvedAmount = calculateApprovedAmount(request.getTotalAmount(), 
                hospitalStayDays, dailyAverageCost);
        
        // 创建理赔结果对象
        final MedicalClaimResult result = MedicalClaimResult.builder()
                .claimId(request.getClaimId())
                .status(MedicalClaimResult.ClaimStatus.APPROVED)
                .processingDate(LocalDateTime.now())
                .totalClaimAmount(request.getTotalAmount())
                .approvedAmount(approvedAmount)
                .rejectedAmount(request.getTotalAmount().subtract(approvedAmount))
                .processorId("SYSTEM")
                .paymentStatus("PENDING")
                .build();
        
        log.info("住院理赔处理完成 | requestId={} | claimId={} | totalAmount={} | approvedAmount={} | hospitalStayDays={}", 
                requestId, request.getClaimId(), request.getTotalAmount(), approvedAmount, hospitalStayDays);
        return result;
    }
    
    
    /**
     * 验证理赔上下文的有效性
     * 
     * @param context 待验证的业务上下文
     * @throws IllegalArgumentException 当上下文为空时抛出
     */
    private void validateClaimContext(final BizContext<MedicalClaimRequest> context) {
        if (context == null) {
            throw new IllegalArgumentException("理赔上下文不能为空");
        }
        if (context.getData() == null) {
            throw new IllegalArgumentException("理赔请求数据不能为空");
        }
    }
    
    /**
     * 验证理赔请求的有效性
     * 
     * @param request 待验证的理赔请求
     * @throws IllegalArgumentException 当请求为空或缺少必要信息时抛出
     */
    private void validateClaimRequest(final MedicalClaimRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("理赔请求信息不能为空");
        }
        
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        
        if (request.getClaimId() == null || request.getClaimId().trim().isEmpty()) {
            throw new IllegalArgumentException("理赔申请ID不能为空");
        }
        
        if (request.getClaimType() == null) {
            throw new IllegalArgumentException("理赔类型不能为空");
        }
    }
    
    /**
     * 验证住院特定信息
     * 
     * @param request 理赔请求
     * @return 验证结果
     */
    private ValidationResult validateInpatientInfo(final MedicalClaimRequest request) {
        // 验证入院和出院日期
        if (request.getAdmissionDate() == null) {
            return ValidationResult.fail("MISSING_ADMISSION_DATE", "入院日期不能为空");
        }
        
        if (request.getDischargeDate() == null) {
            return ValidationResult.fail("MISSING_DISCHARGE_DATE", "出院日期不能为空");
        }
        
        // 验证入院日期不能晚于出院日期
        if (request.getAdmissionDate().isAfter(request.getDischargeDate())) {
            return ValidationResult.fail("INVALID_DATE_RANGE", "入院日期不能晚于出院日期");
        }
        
        // 验证入院类型
        if (request.getAdmissionType() == null || request.getAdmissionType().trim().isEmpty()) {
            return ValidationResult.fail("MISSING_ADMISSION_TYPE", "入院类型不能为空");
        }
        
        // 计算并验证住院天数
        long hospitalStayDays = calculateHospitalStayDays(request);
        if (hospitalStayDays < MIN_HOSPITAL_STAY_DAYS) {
            return ValidationResult.fail("INVALID_STAY_DURATION", 
                    "住院天数必须大于等于" + MIN_HOSPITAL_STAY_DAYS + "天");
        }
        
        if (hospitalStayDays > MAX_HOSPITAL_STAY_DAYS) {
            return ValidationResult.fail("EXCESSIVE_STAY_DURATION", 
                    "住院天数不能超过" + MAX_HOSPITAL_STAY_DAYS + "天");
        }
        
        // 验证每日费用是否在合理范围内
        BigDecimal dailyAverageCost = calculateDailyAverageCost(request.getTotalAmount(), hospitalStayDays);
        if (dailyAverageCost.compareTo(DAILY_COST_LIMIT) > 0) {
            return ValidationResult.fail("EXCESSIVE_DAILY_COST", 
                    "每日平均费用超出限制，限制金额: " + DAILY_COST_LIMIT);
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 计算住院天数
     * 
     * @param request 理赔请求
     * @return 住院天数
     */
    private long calculateHospitalStayDays(final MedicalClaimRequest request) {
        LocalDate admissionDate = request.getAdmissionDate().toLocalDate();
        LocalDate dischargeDate = request.getDischargeDate().toLocalDate();
        
        // 计算日期差并加1（包括入院当天）
        return ChronoUnit.DAYS.between(admissionDate, dischargeDate) + 1;
    }
    
    /**
     * 计算每日平均费用
     * 
     * @param totalAmount 总费用
     * @param days 天数
     * @return 每日平均费用
     */
    private BigDecimal calculateDailyAverageCost(final BigDecimal totalAmount, final long days) {
        if (days <= 0) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(new BigDecimal(days), 2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * 计算批准的理赔金额
     * 
     * @param totalAmount 总费用
     * @param days 住院天数
     * @param dailyCost 每日费用
     * @return 批准的理赔金额
     */
    private BigDecimal calculateApprovedAmount(final BigDecimal totalAmount, 
                                             final long days, 
                                             final BigDecimal dailyCost) {
        // 计算基础报销金额
        BigDecimal baseApprovedAmount = totalAmount.multiply(REIMBURSEMENT_RATE)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        
        // 根据住院天数可能的额外调整逻辑可在此扩展
        // 例如：对于长期住院可能有特殊政策
        
        return baseApprovedAmount;
    }
}