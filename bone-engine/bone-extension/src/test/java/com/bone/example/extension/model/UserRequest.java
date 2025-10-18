package com.bone.example.extension.model;

import lombok.Data;
import java.util.Map;

/**
 * 用户请求模型类
 * 封装用户服务相关的请求信息
 */
@Data
public class UserRequest {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户信息
     */
    private UserInfo userInfo;
    
    /**
     * 操作类型
     * 例如：QUERY（查询）、CREATE（创建）、UPDATE（更新）、DISABLE（禁用）
     */
    private String operationType;
    
    /**
     * 扩展参数
     * 存储额外的请求参数
     */
    private Map<String, Object> extraParams;
}