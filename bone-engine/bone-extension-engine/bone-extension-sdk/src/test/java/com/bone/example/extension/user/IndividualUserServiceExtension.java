package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.example.extension.model.UserInfo;
import com.bone.example.extension.model.UserRequest;
import com.bone.example.extension.model.UserResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 个人用户服务扩展实现
 * 处理个人用户的特定操作逻辑
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "个人用户服务扩展实现",
    description = "处理个人用户的特定操作和业务逻辑",
    tenantCode = "default",
    bizCode = "INDIVIDUAL_USER",
    scenario = "INDIVIDUAL_USER_SERVICE",
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
     description = "个人用户服务的具体实现，处理个人用户的各类操作。",
     scenarios = "适用于个人用户注册、登录、信息管理等场景",
     implementationDetails = "处理个人用户的业务逻辑，包括身份验证、信息存储等",
     differences = "与企业用户服务相比，更注重个人隐私保护和单用户操作",
     notes = "测试使用的简化实现",
     author = "测试团队",
     createDate = "2024-01-01"
  )
@Component
public class IndividualUserServiceExtension implements UserServiceExtPoint {
    
    // 模拟个人用户数据存储
    private final Map<String, UserInfo> individualUserStore = new HashMap<>();
    
    @Override
    public UserResult process(BizContext<?> context) {
        // 确保上下文数据类型正确
        if (!(context.getData() instanceof UserRequest)) {
            return UserResult.fail("INVALID_CONTEXT", "Invalid context data type");
        }
        
        UserRequest request = (UserRequest) context.getData();
        
        // 简化实现，直接返回成功
        return UserResult.success(null);
    }
    
    /**
     * 查询个人用户信息
     */
    private UserResult queryIndividualUser(String userId) {
        // 简化实现，不使用log
        UserInfo userInfo = individualUserStore.get(userId);
        if (userInfo == null) {
            return UserResult.fail("USER_NOT_FOUND", "Individual user not found: " + userId);
        }
        
        // 简化处理，不更新时间
        return UserResult.success(userInfo);
    }
    
    /**
     * 创建个人用户
     */
    private UserResult createIndividualUser(UserInfo userInfo) {
        // 进一步简化实现
        return UserResult.success(userInfo);
    }
    
    /**
     * 更新个人用户信息
     */
    private UserResult updateIndividualUser(UserInfo userInfo) {
        // 简化实现，不使用log和不存在的方法
        return UserResult.success(userInfo);
    }
    
    /**
     * 禁用个人用户
     */
    private UserResult disableIndividualUser(String userId) {
        // 简化实现，不使用log和不存在的方法
        return UserResult.success(null);
    }
    
    /**
     * 验证个人用户信息
     */
    private boolean validateIndividualUser(UserInfo userInfo) {
        // 简化实现，只检查null
        return userInfo != null;
    }
    
    /**
     * 发送欢迎通知
     */
    private void sendWelcomeNotification(UserInfo userInfo) {
        // 简化实现，不使用任何方法调用
    }
    
    @Override
    public int getPriority() {
        // 个人用户扩展点优先级高于默认扩展点，但低于企业和VIP用户
        return 15;
    }
    
    @Override
    public boolean isApplicable(BizContext<?> context) {
        // 简化实现，直接返回false
        return false;
    }
}