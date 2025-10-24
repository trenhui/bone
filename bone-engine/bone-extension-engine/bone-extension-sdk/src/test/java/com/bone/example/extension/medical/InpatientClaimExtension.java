package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.example.extension.result.ValidationResult;

/**
 * 住院理赔处理扩展实现
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "住院理赔扩展实现",
    description = "处理住院类型的医疗保险理赔",
    tenantCode = "default",
    bizCode = "MEDICAL_CLAIM",
    scenario = "INPATIENT_CLAIM",
    condition = "#data.claimType == T(com.bone.example.extension.medical.MedicalClaimRequest.ClaimType).INPATIENT",
    priority = 110,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "专门处理住院类型的医疗保险理赔，包括住院费用验证和理赔计算。",
    scenarios = "适用于住院就医后的理赔场景",
    implementationDetails = "验证住院就诊信息，计算住院期间所有费用的理赔金额",
    differences = "与门诊理赔相比，需要验证更多住院相关信息，理赔标准和限额不同",
    notes = "测试使用的简化实现，返回固定的成功结果",
    author = "测试团队",
    createDate = "2024-01-01"
)
public class InpatientClaimExtension implements MedicalClaimExtPoint {
    
    @Override
    public ValidationResult validateClaim(BizContext<MedicalClaimRequest> context) {
        // 简化实现，直接返回成功结果
        return ValidationResult.success();
    }
    
    @Override
    public MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context) {
        // 简化实现，直接返回空结果
        return new MedicalClaimResult();
    }
    
    @Override
    public MedicalClaimRequest.ClaimType getSupportedClaimType() {
        // 返回支持的理赔类型
        return MedicalClaimRequest.ClaimType.INPATIENT;
    }
}