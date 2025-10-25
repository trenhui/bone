package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.annotation.ExtensionDoc;

/**
 * VIP用户服务扩展实现
 * <p>
 * 专门处理VIP用户的服务请求，提供高级用户体验和特权功能
 */
@ExtensionDoc(
    description = "VIP用户服务处理实现",
    notes = "为VIP用户提供专属服务，包括优先处理和特权功能"
)
public class VipUserServiceExtension implements UserServiceExtPoint {

    /**
     * 处理VIP用户服务请求
     * <p>
     * 为VIP用户提供专属的服务处理逻辑，设置VIP标识
     * 
     * @param context 业务上下文，包含用户请求信息
     * @return VIP用户处理结果，包含VIP用户标识信息
     */
    @Override
    public UserResult process(BizContext<?> context) {
        // 创建VIP用户结果对象
        UserResult result = new UserResult();
        
        // 设置VIP用户标识
        result.setUserId("vip-user-id");
        result.setUsername("vip-user");
        result.setSuccess(true);
        
        return result;
    }
    
    /**
     * 获取VIP扩展点优先级
     * <p>
     * 设置较高优先级，确保VIP用户请求优先被此实现处理
     * 
     * @return 高优先级值：100
     */
    @Override
    public int getPriority() {
        return 100; // 高优先级，确保VIP用户请求优先处理
    }
    
    /**
     * 检查是否适用于当前请求
     * <p>
     * 可以根据上下文信息判断是否为VIP用户请求
     * 
     * @param context 业务上下文
     * @return 是否适用
     */
    @Override
    public boolean isApplicable(BizContext<?> context) {
        // 实际实现中应根据上下文判断用户是否为VIP
        return true; // 简化实现，默认适用于所有请求
    }
}