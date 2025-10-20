package com.bone.example.extension.user;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.model.UserInfo;
import com.bone.example.extension.model.UserRequest;
import com.bone.example.extension.model.UserResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 默认用户服务扩展实现
 * 处理普通用户的基本操作
 */
@Extension(bizCode = "DEFAULT_USER")
@Slf4j
public class DefaultUserServiceExtension implements UserServiceExtPoint {
    
    // 模拟数据存储
    private final Map<String, UserInfo> userStore = new HashMap<>();
    
    @Override
    public UserResult process(BizContext<?> context) {
        // 确保上下文数据类型正确
        if (!(context.getData() instanceof UserRequest)) {
            return UserResult.fail("INVALID_CONTEXT", "Invalid context data type");
        }
        
        UserRequest request = (UserRequest) context.getData();
        
        // 根据操作类型处理请求
        switch (request.getOperationType()) {
            case "QUERY":
                return queryUser(request.getUserId());
            case "CREATE":
                return createUser(request.getUserInfo());
            case "UPDATE":
                return updateUser(request.getUserInfo());
            case "DISABLE":
                return disableUser(request.getUserId());
            default:
                return UserResult.fail("UNKNOWN_OPERATION", "Unknown operation type: " + request.getOperationType());
        }
    }
    
    /**
     * 查询用户信息
     */
    private UserResult queryUser(String userId) {
        log.info("Querying user: {}", userId);
        
        UserInfo userInfo = userStore.get(userId);
        if (userInfo == null) {
            return UserResult.fail("USER_NOT_FOUND", "User not found: " + userId);
        }
        
        // 更新最后查询时间（模拟）
        userInfo.setLastLoginTime(new Date());
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 创建用户
     */
    private UserResult createUser(UserInfo userInfo) {
        log.info("Creating user: {}", userInfo.getUserId());
        
        // 检查用户是否已存在
        if (userStore.containsKey(userInfo.getUserId())) {
            return UserResult.fail("USER_EXISTS", "User already exists: " + userInfo.getUserId());
        }
        
        // 设置默认值
        if (userInfo.getUserType() == null) {
            userInfo.setUserType("DEFAULT");
        }
        
        if (userInfo.getStatus() == null) {
            userInfo.setStatus("ACTIVE");
        }
        
        if (userInfo.getCreateTime() == null) {
            userInfo.setCreateTime(new Date());
        }
        
        // 保存用户信息
        userStore.put(userInfo.getUserId(), userInfo);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 更新用户信息
     */
    private UserResult updateUser(UserInfo userInfo) {
        log.info("Updating user: {}", userInfo.getUserId());
        
        // 检查用户是否存在
        UserInfo existingUser = userStore.get(userInfo.getUserId());
        if (existingUser == null) {
            return UserResult.fail("USER_NOT_FOUND", "User not found: " + userInfo.getUserId());
        }
        
        // 更新用户信息（保留创建时间）
        Date createTime = existingUser.getCreateTime();
        userStore.put(userInfo.getUserId(), userInfo);
        userInfo.setCreateTime(createTime);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 禁用用户
     */
    private UserResult disableUser(String userId) {
        log.info("Disabling user: {}", userId);
        
        // 检查用户是否存在
        UserInfo userInfo = userStore.get(userId);
        if (userInfo == null) {
            return UserResult.fail("USER_NOT_FOUND", "User not found: " + userId);
        }
        
        // 检查用户是否已禁用
        if ("DISABLED".equals(userInfo.getStatus())) {
            return UserResult.fail("USER_ALREADY_DISABLED", "User already disabled: " + userId);
        }
        
        // 禁用用户
        userInfo.setStatus("DISABLED");
        userStore.put(userId, userInfo);
        
        return UserResult.success(userInfo);
    }
    
    @Override
    public int getPriority() {
        // 默认优先级为10
        return 10;
    }
    
    @Override
    public boolean isApplicable(BizContext<?> context) {
        // 默认扩展点适用于所有请求
        return true;
    }
}