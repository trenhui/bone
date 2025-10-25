package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import org.springframework.stereotype.Component;

/**
 * 个人用户服务扩展实现
 * <p>
 * 专门处理个人用户的服务请求，提供个性化的用户体验和功能
 */
@Component
@Extension(bizCode = "individual")
@ExtensionDoc(
    description = "个人用户服务处理实现",
    notes = "适用于个人用户场景"
)
public class IndividualUserServiceExtension implements UserServiceExtPoint {

    /**
     * 处理个人用户服务请求
     * <p>
     * 为个人用户提供专属的服务处理逻辑，设置个人用户标识
     * 
     * @param context 业务上下文，包含用户请求信息
     * @return 个人用户处理结果，包含个人用户标识信息
     */
    @Override
    public UserResult process(BizContext<?> context) {
        // 创建个人用户结果对象
        UserResult result = new UserResult();
        
        // 设置个人用户标识
        result.setUserId("individual-user-id");
        result.setUsername("individual-user");
        result.setSuccess(true);
        
        return result;
    }
    
    /**
     * 获取个人用户扩展点优先级
     * <p>
     * 设置中等优先级，确保个人用户请求被适当处理
     * 
     * @return 中等优先级值：30
     */
    @Override
    public int getPriority() {
        return 30; // 中等优先级
    }
    
    /**
     * 检查是否适用于当前请求
     * <p>
     * 可以根据上下文信息判断是否为个人用户请求
     * 
     * @param context 业务上下文
     * @return 是否适用
     */
    @Override
    public boolean isApplicable(BizContext<?> context) {
        // 实际实现中应根据上下文判断用户是否为个人用户
        return context != null; // 简化实现，默认适用于所有非空请求
    }
}