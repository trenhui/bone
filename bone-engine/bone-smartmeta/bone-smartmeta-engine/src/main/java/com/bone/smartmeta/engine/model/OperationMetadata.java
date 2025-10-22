package com.bone.smartmeta.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作元数据模型类
 * 用于定义实体的各种操作和方法
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationMetadata {
    // 基础信息
    private String id;
    private String name;
    private String apiName;
    private String label;
    @Builder.Default
    private Map<String, String> labels = new HashMap<>(); // 多语言标签
    private String description;
    private String domain;
    
    // 操作定义
    private String entityName;
    private String methodName;
    private String implementationClass;
    private String returnType;
    private String scriptLanguage;
    private String scriptContent;
    
    // 参数信息
    @Builder.Default
    private List<OperationParameterMetadata> parameters = new ArrayList<>();
    
    // 权限信息
    private boolean requiresAuth = true;
    private String requiredPermission;
    private String sensitivityLevel;
    
    // 缓存配置
    private boolean cacheable = false;
    private int cacheTtl = 300; // 默认5分钟
    
    // 限流配置
    private boolean rateLimited = false;
    private int maxRequestsPerMinute;
    
    // 监控配置
    private boolean trackPerformance = false;
    private boolean trackAudit = true;
    
    // 业务状态
    private boolean active = true;
    private boolean system = false;
    
    // 生命周期信息
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    // 元数据版本信息
    private String version;
    private String previousVersionId;
    
    /**
     * 获取API名称
     */
    public String getApiName() {
        return this.apiName;
    }
    
    /**
     * 添加操作参数
     */
    public void addParameter(OperationParameterMetadata parameter) {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }
        parameters.add(parameter);
    }
    
    /**
     * 获取参数元数据
     */
    public OperationParameterMetadata getParameter(String parameterName) {
        if (parameters == null) {
            return null;
        }
        return parameters.stream()
                .filter(p -> p.getName().equals(parameterName))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 参数元数据内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationParameterMetadata {
        private String id;
        private String name;
        private String label;
        private String type;
        private boolean required = false;
        private String defaultValue;
        private String description;
        private boolean encrypted = false;
        private String validationPattern;
        
        /**
         * 获取参数名称
         */
        public String getName() {
            return this.name;
        }
    }
}