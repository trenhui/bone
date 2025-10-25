package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.annotation.ExtensionDoc;

/**
 * 默认用户服务扩展实现
 * <p>
 * 作为兜底的用户服务实现，当没有其他特定类型的用户服务实现时使用
 */
@ExtensionDoc(
    description = "默认用户服务处理实现",
    notes = "作为所有用户类型的默认处理逻辑，提供基础的用户服务功能"
)
public class DefaultUserServiceExtension implements UserServiceExtPoint {

    /**
     * 处理默认用户服务请求
     * <p>
     * 为未指定类型的用户提供标准的服务处理逻辑
     * 
     * @param context 业务上下文，包含用户请求信息
     * @return 默认用户处理结果，包含基本用户标识信息
     */
    @Override
    public UserResult process(BizContext<?> context) {
        // 创建用户结果对象
        UserResult result = new UserResult();
        
        // 设置默认用户标识
        result.setUserId("default-user-id");
        result.setUsername("default-user");
        result.setSuccess(true);
        
        return result;
    }
    
    /**
     * 获取默认扩展点优先级
     * <p>
     * 默认优先级设置为最低，确保特定类型的用户服务实现优先被使用
     * 
     * @return 最低优先级值：0
     */
    @Override
    public int getPriority() {
        return 0; // 最低优先级，确保特定实现优先
    }
}