package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.model.UserInfo;
import com.bone.example.extension.model.UserRequest;
import com.bone.example.extension.model.UserResult;
import java.util.Date;

/**
 * 企业用户服务扩展实现
 * 处理企业用户的特定操作逻辑
 */
@Extension(bizCode = "ENTERPRISE_USER")
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