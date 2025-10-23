package com.bone.example.extension.user;

import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.model.UserResult;
import java.util.Date;
import com.bone.example.extension.model.UserInfo;

/**
 * VIP用户服务扩展实现
 */
public class VipUserServiceExtension implements UserServiceExtPoint {

    @Override
    public UserResult process(BizContext<?> context) {
        // 使用Builder模式创建UserInfo对象
        UserInfo userInfo = UserInfo.builder()
                .username("VIP User")
                .userType("VIP")
                .status("ACTIVE")
                .phoneNumber("00000000000")
                .email("vip@example.com")
                .createTime(new Date())
                .lastLoginTime(new Date())
                .remarks("VIP user")
                .build();
        
        return UserResult.success(userInfo);
    }
}