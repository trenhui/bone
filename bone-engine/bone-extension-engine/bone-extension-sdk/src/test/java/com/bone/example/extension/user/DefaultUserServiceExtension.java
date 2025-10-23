package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.model.UserInfo;
import com.bone.example.extension.model.UserResult;
import java.util.Date;

/**
 * 默认用户服务扩展实现
 */
public class DefaultUserServiceExtension implements UserServiceExtPoint {
    
    @Override
    public UserResult process(BizContext<?> context) {
        // 简化实现，直接返回成功结果
        // 使用Builder模式创建UserInfo对象
        return UserResult.success(UserInfo.builder()
                .userId("default")
                .username("default")
                .userType("DEFAULT")
                .status("ACTIVE")
                .phoneNumber("00000000000")
                .email("default@example.com")
                .createTime(new Date())
                .lastLoginTime(new Date())
                .remarks("Default user")
                .build());
    }
}