package com.bone.procurement.engine.model;

import lombok.Data;
import lombok.Builder;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * 规则执行上下文类
 * 提供业务规则执行所需的环境变量和上下文信息
 */
@Data
@Builder
public class RuleExecutionContext {
    
    /**
     * 当前租户ID
     */
    private String tenantId;
    
    /**
     * 当前用户ID
     */
    private String userId;
    
    /**
     * 操作用户名称
     */
    private String userName;
    
    /**
     * 执行时间
     */
    private LocalDateTime executionTime;
    
    /**
     * 扩展属性，用于存储额外的上下文信息
     */
    private Map<String, Object> attributes;
    
    /**
     * 初始化上下文
     */
    public RuleExecutionContext() {
        this.executionTime = LocalDateTime.now();
        this.attributes = new HashMap<>();
    }
    
    /**
     * 设置属性值
     */
    public void setAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(key, value);
    }
    
    /**
     * 获取属性值
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        if (attributes == null) {
            return null;
        }
        return (T) attributes.get(key);
    }
    
    /**
     * 检查属性是否存在
     */
    public boolean hasAttribute(String key) {
        return attributes != null && attributes.containsKey(key);
    }
    
    /**
     * 创建默认的执行上下文
     */
    public static RuleExecutionContext createDefault() {
        return RuleExecutionContext.builder()
                .executionTime(LocalDateTime.now())
                .attributes(new HashMap<>())
                .build();
    }
}