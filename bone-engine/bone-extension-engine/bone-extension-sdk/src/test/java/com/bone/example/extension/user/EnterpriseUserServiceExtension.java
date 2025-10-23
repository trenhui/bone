package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.example.extension.model.UserInfo;
import com.bone.example.extension.model.UserRequest;
import com.bone.example.extension.model.UserResult;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * 企业用户服务扩展实现
 * 处理企业用户的特定操作逻辑
 */
@Extension(
    name = "企业用户服务实现",
    description = "为企业用户提供专属的服务处理逻辑",
    tenantCode = "*",
    bizCode = "ENTERPRISE_USER",
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
@ExtensionDoc(
    description = "企业用户专属的服务实现，处理企业用户相关的业务逻辑。",
    scenarios = "企业用户的注册、登录、组织管理等场景",
    implementationDetails = "实现企业用户的认证、组织架构管理等功能",
    performance = "测试实现，单次执行耗时<5ms",
    notes = "针对企业用户的专用服务实现",
    author = "测试团队",
    createDate = "2024-01-01"
)
@Component
public class EnterpriseUserServiceExtension implements UserServiceExtPoint {
    
    @Override
    public UserResult process(BizContext<?> context) {
        // 简化实现，返回成功结果
        // 使用Builder模式创建UserInfo对象
        return UserResult.success(UserInfo.builder()
                .userId("default")
                .username("default")
                .userType("ENTERPRISE")
                .status("ACTIVE")
                .phoneNumber("00000000000")
                .email("default@example.com")
                .createTime(new Date())
                .lastLoginTime(new Date())
                .remarks("Default enterprise user")
                .build());
    }
    
    @Override
    public int getPriority() {
        return 20;
    }
    
    @Override
    public boolean isApplicable(BizContext<?> context) {
        // 简化判断
        return context != null;
    }
}