package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import org.springframework.stereotype.Component;

/**
 * 企业用户服务扩展实现
 * <p>
 * 专门处理企业用户的服务请求，提供企业级功能和安全保障
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "企业用户服务扩展实现",
    description = "专门处理企业用户的服务请求，提供企业级功能和安全保障",
    tenantCode = "*",
    bizCode = "ENTERPRISE_USER",
    scenario = "ENTERPRISE_USER_SERVICE",
    condition = "#root.getBizContext().getData() != null && (" +
               "#root.getBizContext().getData().getUserType() == 'ENTERPRISE' || " +
               "#root.getBizContext().getData().getUserType() == 'COMPANY' || " +
               "(#root.getBizContext().getData().getEnterpriseLevel() != null && " +
               "#root.getBizContext().getData().getEnterpriseLevel() >= 1))",
    priority = 50,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "企业用户专属服务处理实现，支持企业级认证和组织管理。",
    scenarios = "企业用户的注册、登录、组织管理、权限控制等场景",
    implementationDetails = "实现企业用户的认证、组织架构管理、多用户授权等功能",
    differences = "与个人用户服务不同，此实现专注于企业级功能和团队协作支持",
    performance = "测试实现，单次执行耗时<5ms，支持高并发企业用户操作",
    notes = "为企业用户提供专属服务，包括企业信息管理、组织架构维护和权限控制",
    author = "测试团队",
    createDate = "2024-01-01"
)
@Component
public class EnterpriseUserServiceExtension implements UserServiceExtPoint {
    
    /**
     * 处理企业用户服务请求
     * <p>
     * 为企业用户提供专属的服务处理逻辑，设置企业用户标识
     * 
     * @param context 业务上下文，包含用户请求信息
     * @return 企业用户处理结果，包含企业用户标识信息
     */
    @Override
    public UserResult process(BizContext<?> context) {
        // 创建企业用户结果对象
        UserResult result = new UserResult();
        
        // 设置企业用户标识
        result.setUserId("enterprise-user-id");
        result.setUsername("enterprise-user");
        result.setSuccess(true);
        
        return result;
    }
    
    /**
     * 获取企业扩展点优先级
     * <p>
     * 设置中等优先级，确保企业用户请求被适当处理
     * 
     * @return 中等优先级值：50
     */
    @Override
    public int getPriority() {
        return 50; // 中等优先级
    }
    
    /**
     * 检查是否适用于当前请求
     * <p>
     * 可以根据上下文信息判断是否为企业用户请求
     * 
     * @param context 业务上下文
     * @return 是否适用
     */
    @Override
    public boolean isApplicable(BizContext<?> context) {
        // 实际实现中应根据上下文判断用户是否为企业用户
        return context != null; // 简化实现，默认适用于所有非空请求
    }
}