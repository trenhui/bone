package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import org.springframework.stereotype.Component;

/**
 * VIP用户服务扩展实现
 * <p>
 * 专门处理VIP用户的服务请求，提供高级用户体验和特权功能
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "VIP用户服务扩展实现",
    description = "专门处理VIP用户的服务请求，提供高级用户体验和特权功能",
    tenantCode = "*",
    bizCode = "VIP_USER",
    scenario = "VIP_USER_SERVICE",
    condition = "#root.getBizContext().getData() != null && (" +
               "#root.getBizContext().getData().getUserType() == 'VIP' || " +
               "(#root.getBizContext().getData().getUserLevel() != null && " +
               "#root.getBizContext().getData().getUserLevel() >= 5))",
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "VIP用户专属服务处理实现，提供高级用户体验和特权功能。",
    scenarios = "VIP用户的专属服务、优先处理、特权访问等场景",
    implementationDetails = "实现VIP用户的特权处理、优先队列、专属功能等高级特性",
    differences = "与普通用户服务不同，此实现提供更高优先级的处理和专属特权功能",
    performance = "测试实现，VIP请求优先处理，单次执行耗时<2ms",
    notes = "为VIP用户提供专属服务，包括优先处理、特权功能和个性化体验",
    author = "测试团队",
    createDate = "2024-01-01"
)
@Component
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