package com.bone.procurement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态模型配置类
 * 用于从配置文件中加载动态模型定义
 */
@Configuration
@ConfigurationProperties(prefix = "bone.dynamic-models")
public class DynamicModelConfig {

    // 是否启用动态模型功能
    private boolean enabled = true;
    
    // 动态模型定义映射表
    private Map<String, DynamicModelDefinition> models = new HashMap<>();
    
    // 动态模型实体扫描包
    private String basePackage = "com.bone.procurement.entity.dynamic";
    
    // 是否在应用启动时自动创建数据库表
    private boolean autoCreateTables = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, DynamicModelDefinition> getModels() {
        return models;
    }

    public void setModels(Map<String, DynamicModelDefinition> models) {
        this.models = models;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public boolean isAutoCreateTables() {
        return autoCreateTables;
    }

    public void setAutoCreateTables(boolean autoCreateTables) {
        this.autoCreateTables = autoCreateTables;
    }

    /**
     * 动态模型定义类
     */
    public static class DynamicModelDefinition {
        // 模型名称（用于显示）
        private String label;
        
        // 模型描述
        private String description;
        
        // 字段定义
        private Map<String, DynamicFieldDefinition> fields = new HashMap<>();
        
        // 是否启用业务规则
        private boolean businessRulesEnabled = false;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, DynamicFieldDefinition> getFields() {
            return fields;
        }

        public void setFields(Map<String, DynamicFieldDefinition> fields) {
            this.fields = fields;
        }

        public boolean isBusinessRulesEnabled() {
            return businessRulesEnabled;
        }

        public void setBusinessRulesEnabled(boolean businessRulesEnabled) {
            this.businessRulesEnabled = businessRulesEnabled;
        }
    }

    /**
     * 动态字段定义类
     */
    public static class DynamicFieldDefinition {
        // 字段类型（string, integer, double, date, boolean等）
        private String type = "string";
        
        // 字段标签（用于显示）
        private String label;
        
        // 字段描述
        private String description;
        
        // 是否必填
        private boolean required = false;
        
        // 最大长度（字符串类型有效）
        private Integer maxLength;
        
        // 最小长度（字符串类型有效）
        private Integer minLength;
        
        // 最小值（数值类型有效）
        private Double minValue;
        
        // 最大值（数值类型有效）
        private Double maxValue;
        
        // 默认值
        private String defaultValue;
        
        // 正则表达式验证
        private String pattern;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public Integer getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(Integer maxLength) {
            this.maxLength = maxLength;
        }

        public Integer getMinLength() {
            return minLength;
        }

        public void setMinLength(Integer minLength) {
            this.minLength = minLength;
        }

        public Double getMinValue() {
            return minValue;
        }

        public void setMinValue(Double minValue) {
            this.minValue = minValue;
        }

        public Double getMaxValue() {
            return maxValue;
        }

        public void setMaxValue(Double maxValue) {
            this.maxValue = maxValue;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }
}