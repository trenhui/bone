package com.bone.example.extension.medical;

import com.bone.engine.extension.api.annotation.ExtensionPoint;
import com.bone.engine.extension.api.annotation.ExtensionPointDoc;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.example.extension.result.ValidationResult;

/**
 * 医疗保险理赔扩展点接口
 *
 * <p>定义医疗保险理赔处理的标准扩展点，所有具体理赔类型的处理实现都需要实现此接口。 提供统一的理赔验证和处理流程，确保各扩展点实现遵循一致的行为模式。
 *
 * <p>扩展点实现负责：
 *
 * <ul>
 *   <li>声明支持的理赔类型
 *   <li>验证理赔请求的有效性和完整性
 *   <li>执行具体的理赔处理逻辑
 *   <li>生成理赔结果
 * </ul>
 */
@ExtensionPoint(name = "医疗保险理赔扩展点", description = "医疗保险理赔处理标准扩展点，支持不同类型理赔处理的统一接口")
@ExtensionPointDoc(
    name = "医疗保险理赔扩展点接口",
    domain = "医疗系统",
    category = "理赔处理",
    description = "该扩展点定义了医疗保险理赔处理的核心流程，包括理赔类型支持声明、理赔请求验证和理赔处理三个关键环节，确保各扩展实现遵循一致的行为模式。",
    usage = "实现该接口并通过@Extension注解注册，系统会根据理赔类型自动选择合适的扩展实现。",
    bestPractice =
        "1. 每个实现类只支持一种理赔类型，保持职责单一\n2. 完整实现验证逻辑，确保数据完整性\n3. 妥善处理异常情况，提供明确的错误信息\n4. 记录关键操作日志，便于问题排查\n5. 考虑性能优化，尤其是在高并发场景下",
    note = "理赔处理涉及财务敏感信息，各实现类需确保安全性和数据一致性")
public interface MedicalClaimExtPoint {

  /**
   * 验证理赔请求
   *
   * <p>对理赔请求进行业务规则验证，确保请求数据满足特定理赔类型的要求。 验证内容应包括但不限于：必要字段检查、业务规则验证、时效性验证等。
   *
   * @param context 业务上下文，包含理赔请求和相关业务信息，不可为null
   * @return 验证结果，包含验证状态和错误信息
   * @throws IllegalArgumentException 当上下文参数为null时抛出
   */
  ValidationResult validateClaim(BizContext<MedicalClaimRequest> context);

  /**
   * 处理理赔请求
   *
   * <p>执行具体的理赔处理逻辑，包括金额计算、报销规则应用等。 此方法应该在验证通过后调用，返回完整的理赔处理结果。
   *
   * @param context 业务上下文，包含理赔请求和相关业务信息，不可为null
   * @return 理赔结果，包含理赔处理状态、金额等详细信息
   * @throws IllegalArgumentException 当上下文参数为null时抛出
   * @throws com.bone.example.extension.medical.exception.MedicalClaimException 当理赔处理过程中发生业务异常时抛出
   */
  MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context);
}
