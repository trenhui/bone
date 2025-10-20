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
 * 个人用户服务扩展实现
 * 处理个人用户的特定操作逻辑
 */
@Extension(bizCode = "INDIVIDUAL_USER")
@Slf4j
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
        
        // 根据操作类型处理请求
        switch (request.getOperationType()) {
            case "QUERY":
                return queryIndividualUser(request.getUserId());
            case "CREATE":
                return createIndividualUser(request.getUserInfo());
            case "UPDATE":
                return updateIndividualUser(request.getUserInfo());
            case "DISABLE":
                return disableIndividualUser(request.getUserId());
            default:
                return UserResult.fail("UNKNOWN_OPERATION", "Unknown operation type: " + request.getOperationType());
        }
    }
    
    /**
     * 查询个人用户信息
     */
    private UserResult queryIndividualUser(String userId) {
        log.info("Querying individual user: {}", userId);
        
        UserInfo userInfo = individualUserStore.get(userId);
        if (userInfo == null) {
            return UserResult.fail("USER_NOT_FOUND", "Individual user not found: " + userId);
        }
        
        // 更新最后查询时间（模拟）
        userInfo.setLastLoginTime(new Date());
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 创建个人用户
     */
    private UserResult createIndividualUser(UserInfo userInfo) {
        log.info("Creating individual user: {}", userInfo.getUserId());
        
        // 验证个人用户必需字段
        if (!validateIndividualUser(userInfo)) {
            return UserResult.fail("INVALID_INDIVIDUAL_USER", "Missing required individual user information");
        }
        
        // 检查用户是否已存在
        if (individualUserStore.containsKey(userInfo.getUserId())) {
            return UserResult.fail("USER_EXISTS", "Individual user already exists: " + userInfo.getUserId());
        }
        
        // 设置个人用户默认值
        userInfo.setUserType("INDIVIDUAL");
        userInfo.setStatus("ACTIVE");
        userInfo.setCreateTime(new Date());
        
        // 保存用户信息
        individualUserStore.put(userInfo.getUserId(), userInfo);
        
        // 个人用户创建后的特殊处理
        sendWelcomeNotification(userInfo);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 更新个人用户信息
     */
    private UserResult updateIndividualUser(UserInfo userInfo) {
        log.info("Updating individual user: {}", userInfo.getUserId());
        
        // 检查用户是否存在
        UserInfo existingUser = individualUserStore.get(userInfo.getUserId());
        if (existingUser == null) {
            return UserResult.fail("USER_NOT_FOUND", "Individual user not found: " + userInfo.getUserId());
        }
        
        // 验证个人用户必需字段
        if (!validateIndividualUser(userInfo)) {
            return UserResult.fail("INVALID_INDIVIDUAL_USER", "Missing required individual user information");
        }
        
        // 更新用户信息（保留创建时间）
        Date createTime = existingUser.getCreateTime();
        userInfo.setUserType("INDIVIDUAL");
        individualUserStore.put(userInfo.getUserId(), userInfo);
        userInfo.setCreateTime(createTime);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 禁用个人用户
     */
    private UserResult disableIndividualUser(String userId) {
        log.info("Disabling individual user: {}", userId);
        
        // 检查用户是否存在
        UserInfo userInfo = individualUserStore.get(userId);
        if (userInfo == null) {
            return UserResult.fail("USER_NOT_FOUND", "Individual user not found: " + userId);
        }
        
        // 检查用户是否已禁用
        if ("DISABLED".equals(userInfo.getStatus())) {
            return UserResult.fail("USER_ALREADY_DISABLED", "Individual user already disabled: " + userId);
        }
        
        // 禁用用户
        userInfo.setStatus("DISABLED");
        individualUserStore.put(userId, userInfo);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 验证个人用户信息
     */
    private boolean validateIndividualUser(UserInfo userInfo) {
        // 个人用户必需的字段验证
        return userInfo != null && 
               userInfo.getUserId() != null && !userInfo.getUserId().trim().isEmpty() &&
               userInfo.getUsername() != null && !userInfo.getUsername().trim().isEmpty() &&
               userInfo.getPhoneNumber() != null && !userInfo.getPhoneNumber().trim().isEmpty();
    }
    
    /**
     * 发送欢迎通知（模拟）
     */
    private void sendWelcomeNotification(UserInfo userInfo) {
        log.info("Sending welcome notification to user: {}", userInfo.getUserId());
        // 这里可以实现实际的通知逻辑，如发送邮件、短信等
    }
    
    @Override
    public int getPriority() {
        // 个人用户扩展点优先级高于默认扩展点，但低于企业和VIP用户
        return 15;
    }
    
    @Override
    public boolean isApplicable(BizContext<?> context) {
        // 只适用于个人用户请求
        if (context.getData() instanceof UserRequest) {
            UserRequest request = (UserRequest) context.getData();
            if (request.getUserInfo() != null) {
                return "INDIVIDUAL".equals(request.getUserInfo().getUserType());
            }
        }
        return false;
    }
}