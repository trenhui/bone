package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;

/**
 * 用户问候扩展点
 * 根据用户类型返回不同的问候语
 * <p>
 * 核心业务场景：
 * - 用户登录欢迎
 * - 定时推送问候
 * - 活动期间特殊问候
 */
@ExtPoint(
    name = "用户问候扩展点",
    description = "根据用户类型返回不同的问候语的扩展点接口",
    version = "1.0.0",
    enabled = true,
    priority = 100,
    enableCache = false,
    timeout = 500
)
@ExtPointDoc(
    description = "该扩展点用于根据用户类型、上下文和环境生成个性化的问候语，支持普通用户和VIP用户的差异化服务。",
    usage = "1. 在用户登录或访问系统首页时调用\n2. 根据上下文信息中的用户类型、等级和业务场景选择合适的实现\n3. 返回包含个性化元素的问候语\n4. 可配合活动营销系统实现特定时期的问候语定制",
    bestPractices = "1. 保持问候语简洁友好，避免过长\n2. 考虑多语言和国际化支持\n3. 确保线程安全，支持高并发调用\n4. 对于频繁调用场景考虑启用缓存\n5. 实现应保持幂等性",
    params = {
        @ExtPointDoc.Param(
            name = "context",
            type = "BizContext<String>",
            description = "业务上下文，包含用户名、用户类型、请求来源等信息",
            required = true,
            example = "BizContext.builder()\n    .data(\"user123\")\n    .bizCode(\"standard\")\n    .scenario(\"login\")\n    .attr(\"userLevel\", \"VIP\")\n    .build()"
        )
    },
    returnInfo = @ExtPointDoc.Return(
            type = "String",
            description = "个性化问候语",
            example = "Hello, user123! Welcome back.",
            errorCodes = {
                @ExtPointDoc.ErrorCode(code = "GREETING_GENERATE_FAILED", description = "问候语生成失败", solution = "检查用户数据是否完整"),
                @ExtPointDoc.ErrorCode(code = "CONTEXT_PARAM_ERROR", description = "上下文参数错误", solution = "验证传入的BizContext是否包含必要信息")
            }
        ),
    notes = "用户交互体验的重要组成部分，良好的问候语可以提升用户满意度。建议根据用户画像和行为数据动态调整问候内容。"
)
public interface GreetingExtPoint {
    /**
     * 向用户问候
     * @param context 业务上下文，包含用户名、用户类型等信息
     * @return 个性化问候语
     * @throws IllegalArgumentException 当上下文参数无效时抛出
     */
    String greet(BizContext<String> context);
}