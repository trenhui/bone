package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;

/**
 * 用户问候扩展点
 * 根据用户类型返回不同的问候语
 */
@ExtPoint(
    name = "用户问候扩展点",
    description = "根据用户类型返回不同的问候语的扩展点接口",
    domain = "用户系统",
    category = "用户交互",
    version = "1.0.0",
    enabled = true,
    priority = 100,
    enableCache = false,
    timeout = 500
)
@ExtPointDoc(
    description = "该扩展点用于根据用户类型生成不同的问候语，支持普通用户和VIP用户。",
    usage = "1. 在用户登录或访问系统时调用\n2. 根据上下文信息中的用户类型选择合适的实现\n3. 返回个性化的问候语",
    bestPractices = "1. 保持问候语简洁友好\n2. 考虑多语言支持\n3. 确保线程安全",
    params = {
        @ExtPointDoc.Param(
            name = "context",
            type = "BizContext<String>",
            description = "业务上下文，包含用户名等信息",
            required = true,
            example = "BizContext.builder().data(\"user123\").build()"
        )
    },
    returnInfo = @ExtPointDoc.Return(
        type = "String",
        description = "问候语",
        successExample = "Hello, user123!"
    ),
    notes = "用户交互场景的核心扩展点，支持多租户和不同用户类型"
)
public interface GreetingExtPoint {
    /**
     * 向用户问候
     * @param context 业务上下文，包含用户名等信息
     * @return 问候语
     */
    String greet(BizContext<String> context);
}