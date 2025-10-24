package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;
import com.bone.example.extension.result.ValidationResult;

/**
 * 医疗保险理赔扩展点
 * 定义了理赔验证和理赔处理的核心方法
 */
// 运行时配置 - 提供扩展点基本信息和默认配置
@ExtPoint(
    name = "医疗保险理赔扩展点",
    description = "处理各类医疗保险理赔的验证和处理逻辑",
    domain = "医疗健康",
    category = "保险理赔",
    version = "1.0.0",
    enabled = true,
    priority = 100
)
// 接口文档 - 详细描述扩展点功能、参数和使用场景（编译时注解，不影响运行时）
@ExtPointDoc(
    title = "医疗保险理赔扩展点接口",
    domain = "医疗健康",
    category = "保险理赔",
    description = "定义了医疗保险理赔的验证和处理标准接口，支持不同类型的理赔处理。",
    usage = "1. 实现接口并添加@Extension注解\n2. 根据理赔类型配置路由条件\n3. 注入到医疗理赔服务层使用",
    bestPractices = "1. 确保理赔验证的严格性和全面性\n2. 针对不同理赔类型提供专门实现\n3. 注意敏感医疗数据的安全处理",
    params = {
        @ExtPointDoc.Param(
            name = "context",
            type = "BizContext<MedicalClaimRequest>",
            description = "包含理赔请求信息的业务上下文",
            required = true
        )
    },
    returnInfo = @ExtPointDoc.Return(
        type = "ValidationResult/MedicalClaimResult",
        description = "理赔验证结果或理赔处理结果"
    ),
    notes = "扩展实现需要根据不同的理赔类型（门诊、住院、特殊疾病等）提供专门的处理逻辑",
    creator = "测试团队",
    createDate = "2024-01-01"
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