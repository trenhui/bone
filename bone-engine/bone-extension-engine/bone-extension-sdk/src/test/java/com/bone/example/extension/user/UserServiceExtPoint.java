package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.example.extension.model.UserResult;
import com.bone.engine.extension.annotation.ExtPointDoc;

/**
 * 用户服务扩展点接口
 * 定义用户服务相关的核心操作方法
 */
@ExtPoint(
    name = "用户服务扩展点",
    description = "处理用户相关操作的扩展点接口",
    domain = "用户系统",
    category = "用户管理",
    version = "1.0.0",
    enabled = true,
    priority = 100,
    enableCache = false,
    timeout = 2000
)
@ExtPointDoc(
    description = "该扩展点用于处理用户相关的各种操作，支持不同类型用户的差异化处理。",
    usage = "1. 在用户注册、登录、信息更新等场景使用\n2. 根据用户类型选择合适的实现\n3. 处理用户权限和认证",
    bestPractices = "1. 确保用户数据的安全性\n2. 考虑权限控制\n3. 实现适当的日志记录",
    notes = "用户管理系统的核心扩展点，支持个人用户和企业用户"
)
public interface UserServiceExtPoint {
    
    /**
     * 处理用户服务请求
     * 根据上下文信息执行相应的用户操作（查询、创建、更新、禁用等）
     * 
     * @param context 业务上下文，包含用户请求信息
     * @return 处理结果
     */
    UserResult process(BizContext<?> context);
    
    /**
     * 获取扩展点的优先级
     * 当多个扩展点可以应用于同一请求时，优先级高的扩展点将被优先选择
     * 
     * @return 扩展点优先级，默认为0
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * 检查扩展点是否适用于当前请求
     * 
     * @param context 业务上下文
     * @return 是否适用
     */
    default boolean isApplicable(BizContext<?> context) {
        return true;
    }
}