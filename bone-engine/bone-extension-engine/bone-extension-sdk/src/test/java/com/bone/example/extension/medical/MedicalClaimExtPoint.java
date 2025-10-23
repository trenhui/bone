package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;
import com.bone.example.extension.result.ValidationResult;

/**
 * 医疗保险理赔扩展点
 * 定义了理赔验证和理赔处理的核心方法
 */
@ExtPoint(
    name = "医疗理赔扩展点",
    description = "处理医疗理赔业务的扩展点接口",
    domain = "医疗系统",
    category = "理赔处理",
    version = "1.0.0",
    enabled = true,
    priority = 100,
    enableCache = false,
    timeout = 3000
)
@ExtPointDoc(
    description = "该扩展点用于处理医疗理赔相关业务，支持不同类型的理赔场景。",
    usage = "1. 在用户提交医疗理赔申请时调用\n2. 根据理赔类型选择合适的实现\n3. 处理理赔审核和计算",
    bestPractices = "1. 确保理赔规则的准确性\n2. 考虑医疗数据的隐私保护\n3. 实现详细的日志记录",
    notes = "医疗理赔系统的核心扩展点，需要严格遵循相关法规"
)
public interface MedicalClaimExtPoint {
    
    /**
     * 验证理赔请求
     * @param context 业务上下文，包含理赔请求信息
     * @return 验证结果
     */
    ValidationResult validateClaim(BizContext<MedicalClaimRequest> context);
    
    /**
     * 处理理赔请求
     * @param context 业务上下文
     * @return 理赔结果
     */
    MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context);
    
    /**
     * 获取支持的理赔类型
     * @return 支持的理赔类型
     */
    MedicalClaimRequest.ClaimType getSupportedClaimType();
}