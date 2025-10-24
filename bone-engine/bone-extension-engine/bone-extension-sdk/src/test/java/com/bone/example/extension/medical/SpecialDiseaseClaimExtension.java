package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 特殊疾病理赔处理扩展实现
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "特殊疾病理赔扩展实现",
    description = "处理特殊疾病类型的医疗保险理赔",
    tenantCode = "default",
    bizCode = "SPECIAL_DISEASE_CLAIM",
    scenario = "SPECIAL_DISEASE_CLAIM",
    condition = "#data.claimType == T(com.bone.example.extension.medical.MedicalClaimRequest.ClaimType).SPECIAL_TREATMENT",
    priority = 120,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
     description = "专门处理特殊疾病类型的医疗保险理赔，包括特殊疾病费用验证和理赔计算。",
     scenarios = "适用于特殊疾病患者的理赔场景",
     implementationDetails = "验证特殊疾病诊断证明，计算特殊疾病相关费用的理赔金额",
     differences = "与普通门诊和住院理赔相比，针对特殊疾病有专门的理赔标准和限额",
     notes = "测试使用的简化实现，返回固定的成功结果",
     author = "测试团队",
     createDate = "2024-01-01"
  )
@Component
public class SpecialDiseaseClaimExtension implements MedicalClaimExtPoint {
    
    @Override
    public ValidationResult validateClaim(BizContext<MedicalClaimRequest> context) {
        // 简化实现，直接返回成功
        return ValidationResult.success();
    }
    
    @Override
    public MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context) {
        // 返回空的MedicalClaimResult对象
        return new MedicalClaimResult();
    }
    
    @Override
    public MedicalClaimRequest.ClaimType getSupportedClaimType() {
        // 返回null
        return null;
    }
}