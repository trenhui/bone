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
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "个人用户服务扩展实现",
    description = "专门处理个人用户的服务请求，提供个性化的用户体验和功能",
    tenantCode = "*",
    bizCode = "INDIVIDUAL_USER",
    scenario = "INDIVIDUAL_USER_SERVICE",
    condition = "#root.getBizContext().getData() != null && (" +
               "#root.getBizContext().getData().getUserType() == 'INDIVIDUAL' || " +
               "#root.getBizContext().getData().getUserType() == 'PERSONAL')",
    priority = 30,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "个人用户专属服务处理实现，提供个性化的用户体验和功能。",
    scenarios = "个人用户的注册、登录、个人信息管理、偏好设置等场景",
    implementationDetails = "实现个人用户的认证、个人资料管理、偏好设置等功能",
    differences = "与企业用户服务不同，此实现专注于个人用户体验和个性化服务",
    performance = "测试实现，单次执行耗时<3ms，满足高并发个人用户操作需求",
    notes = "适用于个人用户场景，提供个性化的用户体验和便捷的服务功能",
    author = "测试团队",
    createDate = "2024-01-01"
)
@Component
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