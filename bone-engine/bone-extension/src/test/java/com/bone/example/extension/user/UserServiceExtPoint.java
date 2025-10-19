package com.bone.example.extension.user;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.example.extension.model.UserResult;

/**
 * 用户服务扩展点接口
 * 定义用户服务相关的核心操作方法
 */
@ExtPoint
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