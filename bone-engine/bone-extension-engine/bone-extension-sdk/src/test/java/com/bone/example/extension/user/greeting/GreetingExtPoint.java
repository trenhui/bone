package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;

/**
 * 用户问候扩展点
 * 根据用户类型返回不同的问候语
 */
@ExtPoint
public interface GreetingExtPoint {
    /**
     * 向用户问候
     * @param context 业务上下文，包含用户名等信息
     * @return 问候语
     */
    String greet(BizContext<String> context);
}