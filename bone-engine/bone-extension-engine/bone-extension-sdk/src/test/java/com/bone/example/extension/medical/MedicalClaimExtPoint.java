package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.result.ValidationResult;

/**
 * 医疗保险理赔扩展点接口
 * <p>
 * 定义医疗保险理赔处理的标准扩展点，所有具体理赔类型的处理实现都需要实现此接口。
 * 提供统一的理赔验证和处理流程，确保各扩展点实现遵循一致的行为模式。
 * <p>
 * 扩展点实现负责：
 * <ul>
 *     <li>声明支持的理赔类型</li>
 *     <li>验证理赔请求的有效性和完整性</li>
 *     <li>执行具体的理赔处理逻辑</li>
 *     <li>生成理赔结果</li>
 * </ul>
 */
public interface MedicalClaimExtPoint {
    /**
     * 获取支持的理赔类型
     * <p>
     * 返回该扩展点实现支持处理的具体理赔类型，系统将根据此信息进行路由。
     * 每个扩展点实现应该只支持一种理赔类型，确保职责单一。
     * 
     * @return 支持的理赔类型，不可返回null
     */
    MedicalClaimRequest.ClaimType getSupportedClaimType();

    /**
     * 验证理赔请求
     * <p>
     * 对理赔请求进行业务规则验证，确保请求数据满足特定理赔类型的要求。
     * 验证内容应包括但不限于：必要字段检查、业务规则验证、时效性验证等。
     * 
     * @param context 业务上下文，包含理赔请求和相关业务信息，不可为null
     * @return 验证结果，包含验证状态和错误信息
     * @throws IllegalArgumentException 当上下文参数为null时抛出
     */
    ValidationResult validateClaim(BizContext<MedicalClaimRequest> context);

    /**
     * 处理理赔请求
     * <p>
     * 执行具体的理赔处理逻辑，包括金额计算、报销规则应用等。
     * 此方法应该在验证通过后调用，返回完整的理赔处理结果。
     * 
     * @param context 业务上下文，包含理赔请求和相关业务信息，不可为null
     * @return 理赔结果，包含理赔处理状态、金额等详细信息
     * @throws IllegalArgumentException 当上下文参数为null时抛出
     * @throws com.bone.example.extension.medical.exception.MedicalClaimException 当理赔处理过程中发生业务异常时抛出
     */
    MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context);
}