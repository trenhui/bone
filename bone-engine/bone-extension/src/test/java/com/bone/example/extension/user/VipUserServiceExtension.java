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
 * VIP用户服务扩展实现
 * 处理VIP用户的特定操作逻辑，提供VIP专属功能
 */
@Extension(bizCode = "VIP_USER")
@Slf4j
public class VipUserServiceExtension implements UserServiceExtPoint {
    
    // 模拟VIP用户数据存储
    private final Map<String, UserInfo> vipUserStore = new HashMap<>();
    
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
                return queryVipUser(request.getUserId());
            case "CREATE":
                return createVipUser(request.getUserInfo());
            case "UPDATE":
                return updateVipUser(request.getUserInfo());
            case "DISABLE":
                return disableVipUser(request.getUserId());
            default:
                return UserResult.fail("UNKNOWN_OPERATION", "Unknown operation type: " + request.getOperationType());
        }
    }
    
    /**
     * 查询VIP用户信息
     */
    private UserResult queryVipUser(String userId) {
        log.info("Querying VIP user: {}", userId);
        
        UserInfo userInfo = vipUserStore.get(userId);
        if (userInfo == null) {
            return UserResult.fail("USER_NOT_FOUND", "VIP user not found: " + userId);
        }
        
        // 更新最后查询时间（模拟）
        userInfo.setLastLoginTime(new Date());
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 创建VIP用户
     */
    private UserResult createVipUser(UserInfo userInfo) {
        log.info("Creating VIP user: {}", userInfo.getUserId());
        
        // 检查用户是否已存在
        if (vipUserStore.containsKey(userInfo.getUserId())) {
            return UserResult.fail("USER_EXISTS", "VIP user already exists: " + userInfo.getUserId());
        }
        
        // 设置VIP用户默认值
        userInfo.setUserType("VIP");
        userInfo.setStatus("ACTIVE");
        userInfo.setCreateTime(new Date());
        
        // 保存用户信息
        vipUserStore.put(userInfo.getUserId(), userInfo);
        
        // VIP用户创建后的特殊处理
        sendVipWelcomeNotification(userInfo);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 更新VIP用户信息
     */
    private UserResult updateVipUser(UserInfo userInfo) {
        log.info("Updating VIP user: {}", userInfo.getUserId());
        
        // 检查用户是否存在
        UserInfo existingUser = vipUserStore.get(userInfo.getUserId());
        if (existingUser == null) {
            return UserResult.fail("USER_NOT_FOUND", "VIP user not found: " + userInfo.getUserId());
        }
        
        // 更新用户信息（保留创建时间）
        Date createTime = existingUser.getCreateTime();
        userInfo.setUserType("VIP");
        vipUserStore.put(userInfo.getUserId(), userInfo);
        userInfo.setCreateTime(createTime);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 禁用VIP用户
     */
    private UserResult disableVipUser(String userId) {
        log.info("Disabling VIP user: {}", userId);
        
        // 检查用户是否存在
        UserInfo userInfo = vipUserStore.get(userId);
        if (userInfo == null) {
            return UserResult.fail("USER_NOT_FOUND", "VIP user not found: " + userId);
        }
        
        // 检查用户是否已禁用
        if ("DISABLED".equals(userInfo.getStatus())) {
            return UserResult.fail("USER_ALREADY_DISABLED", "VIP user already disabled: " + userId);
        }
        
        // VIP用户禁用前的特殊处理（例如通知客户经理）
        notifyVipManager(userId);
        
        // 禁用用户
        userInfo.setStatus("DISABLED");
        vipUserStore.put(userId, userInfo);
        
        return UserResult.success(userInfo);
    }
    
    /**
     * 发送VIP欢迎通知（模拟）
     */
    private void sendVipWelcomeNotification(UserInfo userInfo) {
        log.info("Sending VIP welcome notification to user: {}", userInfo.getUserId());
        // 这里可以实现实际的通知逻辑，如发送邮件、短信等
    }
    
    /**
     * 通知VIP客户经理（模拟）
     */
    private void notifyVipManager(String userId) {
        log.info("Notifying VIP manager about user disable: {}", userId);
        // 这里可以实现实际的通知逻辑
    }
    
    @Override
    public int getPriority() {
        // VIP用户扩展点优先级高于其他扩展点
        return 30;
    }
    
    @Override
    public boolean isApplicable(BizContext<?> context) {
        // 只适用于VIP用户请求
        if (context.getData() instanceof UserRequest) {
            UserRequest request = (UserRequest) context.getData();
            if (request.getUserInfo() != null) {
                return "VIP".equals(request.getUserInfo().getUserType());
            }
        }
        return false;
    }
}