package com.bone.example.extension.model;

import lombok.Builder;
import lombok.Data;

/**
 * 用户结果模型类
 * 封装用户服务相关的处理结果
 */
@Data
@Builder
public class UserResult {
    
    /**
     * 处理是否成功
     */
    private boolean success;
    
    /**
     * 错误代码
     */
    private String errorCode;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 用户信息
     * 成功时包含查询或处理后的用户信息
     */
    private UserInfo userInfo;
    
    /**
     * 结果描述
     */
    private String description;
    
    /**
     * 结果时间戳
     */
    private long timestamp;
    
    /**
     * 创建成功结果
     */
    public static UserResult success(UserInfo userInfo) {
        return UserResult.builder()
            .success(true)
            .userInfo(userInfo)
            .timestamp(System.currentTimeMillis())
            .build();
    }
    
    /**
     * 创建失败结果
     */
    public static UserResult fail(String errorCode, String errorMessage) {
        return UserResult.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}