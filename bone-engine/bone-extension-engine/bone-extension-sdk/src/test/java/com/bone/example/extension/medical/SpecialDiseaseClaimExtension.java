package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.example.extension.result.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * 特殊疾病理赔扩展点实现类
 * <p>
 * 专门处理特殊疾病类型的医疗保险理赔，提供符合特殊疾病理赔标准的验证和处理逻辑。
 * 实现了对特殊疾病诊断证明、用药记录和治疗方案的验证和理赔金额计算。
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "特殊疾病理赔扩展实现",
    description = "处理特殊疾病类型的医疗保险理赔请求",
    tenantCode = "default",
    bizCode = "MEDICAL_CLAIM",  // 统一使用MEDICAL_CLAIM作为业务编码
    scenario = "SPECIAL_DISEASE_CLAIM",
    condition = "#data.claimType == T(com.bone.example.extension.medical.MedicalClaimRequest.ClaimType).SPECIAL_TREATMENT",
    priority = 120,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "专门处理特殊疾病类型的医疗保险理赔，包括特殊疾病费用验证和理赔计算",
    scenarios = "适用于特殊疾病患者的理赔场景",
    implementationDetails = "验证特殊疾病诊断证明，计算特殊疾病相关费用的理赔金额",
    differences = "与普通门诊和住院理赔相比，针对特殊疾病有专门的理赔标准和限额",
    notes = "提供了基础的特殊疾病理赔处理逻辑",
    author = "测试团队",
    createDate = "2024-01-01"
)
public class SpecialDiseaseClaimExtension implements MedicalClaimExtPoint {
    // 日志记录器
    private static final Logger logger = LoggerFactory.getLogger(SpecialDiseaseClaimExtension.class);
    
    // 常量定义
    private static final BigDecimal REIMBURSEMENT_RATE = new BigDecimal("0.9"); // 特殊疾病理赔报销比例90%
    
    @Override
    public ValidationResult validateClaim(final BizContext<MedicalClaimRequest> context) {
        logger.info("开始验证特殊疾病理赔请求");
        
        // 验证上下文和请求数据
        if (context == null || context.getData() == null) {
            logger.warn("特殊疾病理赔请求上下文或数据为空");
            return ValidationResult.fail("INVALID_REQUEST", "理赔请求信息不完整");
        }
        
        final MedicalClaimRequest request = context.getData();
        
        // 验证特殊疾病必要信息
        ValidationResult validationResult = validateSpecialDiseaseInfo(request);
        if (!validationResult.isSuccess()) {
            logger.warn("特殊疾病理赔必要信息验证失败，用户ID: {}, 错误: {}", 
                    request.getUserId(), validationResult.getErrorMessage());
            return validationResult;
        }
        
        logger.info("特殊疾病理赔请求验证通过，用户ID: {}, 诊断信息: {}", 
                request.getUserId(), request.getDiagnosis());
        return ValidationResult.success();
    }
    
    @Override
    public MedicalClaimResult processClaim(final BizContext<MedicalClaimRequest> context) {
        logger.info("开始处理特殊疾病理赔请求");
        
        if (context == null || context.getData() == null) {
            throw new IllegalArgumentException("理赔请求信息不完整");
        }
        
        final MedicalClaimRequest request = context.getData();
        
        // 创建理赔结果对象
        final MedicalClaimResult result = MedicalClaimResult.builder()
                .claimId(request.getClaimId())
                .status(MedicalClaimResult.ClaimStatus.APPROVED)
                .processingDate(new java.util.Date())
                .totalClaimAmount(request.getTotalAmount())
                .build();
        
        // 计算批准金额，应用特殊疾病理赔高报销比例
        BigDecimal approvedAmount = calculateApprovedAmount(request.getTotalAmount());
        result.setApprovedAmount(approvedAmount);
        result.setRejectedAmount(request.getTotalAmount().subtract(approvedAmount));
        
        logger.info("特殊疾病理赔处理完成，理赔ID: {}, 诊断: {}, 申请金额: {}, 批准金额: {}", 
                request.getClaimId(), request.getDiagnosis(), request.getTotalAmount(), approvedAmount);
        return result;
    }
    
    /**
     * 验证特殊疾病必要信息
     * 
     * @param request 理赔请求
     * @return 验证结果
     */
    private ValidationResult validateSpecialDiseaseInfo(final MedicalClaimRequest request) {
        if (request.getDiagnosis() == null || request.getDiagnosis().trim().isEmpty()) {
            return ValidationResult.fail("MISSING_DIAGNOSIS", "特殊疾病理赔必须提供诊断信息");
        }
        
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            return ValidationResult.fail("MISSING_USER_ID", "用户ID不能为空");
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
        return totalAmount.multiply(REIMBURSEMENT_RATE)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
    
    @Override
    public MedicalClaimRequest.ClaimType getSupportedClaimType() {
        // 返回支持的理赔类型
        return MedicalClaimRequest.ClaimType.SPECIAL_TREATMENT;
    }
}