package com.bone.example.extension.medical;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.example.extension.result.ValidationResult;

/**
 * 医疗保险理赔扩展点
 * 定义了理赔验证和理赔处理的核心方法
 */
@ExtPoint
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