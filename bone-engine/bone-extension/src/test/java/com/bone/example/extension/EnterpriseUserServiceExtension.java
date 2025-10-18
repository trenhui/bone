package com.bone.example.extension;

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
 * 企业用户服务扩展实现
 * 处理企业用户的特定操作逻辑
 */
@Extension(bizCode = "ENTERPRISE_USER")
@Slf4j
public class EnterpriseUserServiceExtension implements UserServiceExtPoint {
    
    // 模拟企业用户数据存储
    private final Map<String, UserInfo> enterpriseUserStore = new HashMap<>();
    
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
                return queryEnterpriseUser(request.getUserId());
            case "CREATE":
                return createEnterpriseUser(request.getUserInfo());
            case "UPDATE":
                return updateEnterpriseUser(request.getUserInfo());
            case "DISABLE":
                return disableEnterpriseUser(request.getUserId());
            default:
                return UserResult.fail("UNKNOWN_OPERATION", "Unknown operation type: " + request.getOperationType());
        }
    }
    
    /**
     * 查询企业用户信息
     */
    private UserResult queryEnterpriseUser(String userId) {
        log.info("Querying enterprise user: {}", userId);
        
        UserInfo userInfo = enterpriseUserStore.get(userId);
        if (userInfo == null) {
            return UserResult.fail("USER_NOT_FOUND", "Enterprise user not found: " + userId);
        }
        
        // 更新最后查询时间（模拟）
        userInfo.setLastLoginTime(new Date());
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 创建企业用户
     */
    private UserResult createEnterpriseUser(UserInfo userInfo) {
        log.info("Creating enterprise user: {}", userInfo.getUserId());
        
        // 验证企业用户必需字段
        if (!validateEnterpriseUser(userInfo)) {
            return UserResult.fail("INVALID_ENTERPRISE_USER", "Missing required enterprise user information");
        }
        
        // 检查用户是否已存在
        if (enterpriseUserStore.containsKey(userInfo.getUserId())) {
            return UserResult.fail("USER_EXISTS", "Enterprise user already exists: " + userInfo.getUserId());
        }
        
        // 设置企业用户默认值
        userInfo.setUserType("ENTERPRISE");
        userInfo.setStatus("ACTIVE");
        userInfo.setCreateTime(new Date());
        
        // 保存用户信息
        enterpriseUserStore.put(userInfo.getUserId(), userInfo);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 更新企业用户信息
     */
    private UserResult updateEnterpriseUser(UserInfo userInfo) {
        log.info("Updating enterprise user: {}", userInfo.getUserId());
        
        // 检查用户是否存在
        UserInfo existingUser = enterpriseUserStore.get(userInfo.getUserId());
        if (existingUser == null) {
            return UserResult.fail("USER_NOT_FOUND", "Enterprise user not found: " + userInfo.getUserId());
        }
        
        // 验证企业用户必需字段
        if (!validateEnterpriseUser(userInfo)) {
            return UserResult.fail("INVALID_ENTERPRISE_USER", "Missing required enterprise user information");
        }
        
        // 更新用户信息（保留创建时间）
        Date createTime = existingUser.getCreateTime();
        userInfo.setUserType("ENTERPRISE");
        enterpriseUserStore.put(userInfo.getUserId(), userInfo);
        userInfo.setCreateTime(createTime);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 禁用企业用户
     */
    private UserResult disableEnterpriseUser(String userId) {
        log.info("Disabling enterprise user: {}", userId);
        
        // 检查用户是否存在
        UserInfo userInfo = enterpriseUserStore.get(userId);
        if (userInfo == null) {
            return UserResult.fail("USER_NOT_FOUND", "Enterprise user not found: " + userId);
        }
        
        // 检查用户是否已禁用
        if ("DISABLED".equals(userInfo.getStatus())) {
            return UserResult.fail("USER_ALREADY_DISABLED", "Enterprise user already disabled: " + userId);
        }
        
        // 企业用户禁用前的特殊处理（例如通知相关联系人）
        notifyEnterpriseDisable(userId);
        
        // 禁用用户
        userInfo.setStatus("DISABLED");
        enterpriseUserStore.put(userId, userInfo);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 验证企业用户信息
     */
    private boolean validateEnterpriseUser(UserInfo userInfo) {
        // 企业用户必需的字段验证
        return userInfo != null && 
               userInfo.getUserId() != null && !userInfo.getUserId().trim().isEmpty() &&
               userInfo.getUsername() != null && !userInfo.getUsername().trim().isEmpty() &&
               userInfo.getPhoneNumber() != null && !userInfo.getPhoneNumber().trim().isEmpty() &&
               userInfo.getEmail() != null && !userInfo.getEmail().trim().isEmpty();
    }
    
    /**
     * 通知企业用户禁用（模拟）
     */
    private void notifyEnterpriseDisable(String userId) {
        log.info("Sending notification about enterprise user disable: {}", userId);
        // 这里可以实现实际的通知逻辑，如发送邮件、短信等
    }
    
    @Override
    public int getPriority() {
        // 企业用户扩展点优先级高于默认扩展点
        return 20;
    }
    
    @Override
    public boolean isApplicable(BizContext<?> context) {
        // 只适用于企业用户请求
        if (context.getData() instanceof UserRequest) {
            UserRequest request = (UserRequest) context.getData();
            if (request.getUserInfo() != null) {
                return "ENTERPRISE".equals(request.getUserInfo().getUserType());
            }
        }
        return false;
    }
}