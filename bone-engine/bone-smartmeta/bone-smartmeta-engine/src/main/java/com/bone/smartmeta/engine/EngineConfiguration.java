package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.DefaultMetadataRegistry;
import com.bone.smartmeta.engine.model.MetadataRegistry;
import com.bone.smartmeta.engine.rule.DefaultBusinessRuleRegistry;
import com.bone.smartmeta.engine.rule.BusinessRuleRegistry;
import lombok.Data;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 智能元数据引擎配置类
 * 管理引擎的各种配置选项
 */
@Data
@ToString(exclude = {"metadataRegistrySupplier", "businessRuleRegistrySupplier"})
public class EngineConfiguration implements Cloneable {
    
    // 是否启用缓存
    private boolean enableCaching = true;
    
    // 元数据变更是否自动刷新缓存
    private boolean autoRefreshCache = true;
    
    // 字段计算引擎是否启用
    private boolean calculationEngineEnabled = true;
    
    // 业务规则引擎是否启用
    private boolean businessRuleEngineEnabled = true;
    
    // setter方法
    public void setEnableCaching(boolean enableCaching) {
        this.enableCaching = enableCaching;
    }
    
    public void setAutoRefreshCache(boolean autoRefreshCache) {
        this.autoRefreshCache = autoRefreshCache;
    }
    
    public void setCalculationEngineEnabled(boolean calculationEngineEnabled) {
        this.calculationEngineEnabled = calculationEngineEnabled;
    }
    
    public void setBusinessRuleEngineEnabled(boolean businessRuleEngineEnabled) {
        this.businessRuleEngineEnabled = businessRuleEngineEnabled;
    }
    
    public void setValidationEngineEnabled(boolean validationEngineEnabled) {
        this.validationEngineEnabled = validationEngineEnabled;
    }
    
    public void setIgnoreNullValidation(boolean ignoreNullValidation) {
        this.ignoreNullValidation = ignoreNullValidation;
    }
    
    public void setMaxCalculationRecursionDepth(int maxCalculationRecursionDepth) {
        this.maxCalculationRecursionDepth = maxCalculationRecursionDepth;
    }
    
    public void setRuleExecutionTimeoutMs(long ruleExecutionTimeoutMs) {
        this.ruleExecutionTimeoutMs = ruleExecutionTimeoutMs;
    }
    
    public void setMetadataRegistrySupplier(Supplier<MetadataRegistry> metadataRegistrySupplier) {
        this.metadataRegistrySupplier = metadataRegistrySupplier;
    }
    
    public void setBusinessRuleValidationEnabled(boolean businessRuleValidationEnabled) {
        this.businessRuleValidationEnabled = businessRuleValidationEnabled;
    }
    
    public void setBusinessRuleExecutionEnabled(boolean businessRuleExecutionEnabled) {
        this.businessRuleExecutionEnabled = businessRuleExecutionEnabled;
    }
    
    public void setCalculationTimeoutMs(long calculationTimeoutMs) {
        this.calculationTimeoutMs = calculationTimeoutMs;
    }
    
    public void setBusinessRuleRegistrySupplier(Supplier<BusinessRuleRegistry> businessRuleRegistrySupplier) {
        this.businessRuleRegistrySupplier = businessRuleRegistrySupplier;
    }
    
    // 验证引擎是否启用
    private boolean validationEngineEnabled = true;
    
    // 是否忽略空值验证
    private boolean ignoreNullValidation = false;
    
    // 字段计算的最大递归深度
    private int maxCalculationRecursionDepth = 10;
    
    // 规则执行的超时时间（毫秒）
    private long ruleExecutionTimeoutMs = 5000;
    
    // 元数据注册表供应商
    private Supplier<MetadataRegistry> metadataRegistrySupplier = DefaultMetadataRegistry::new;
    
    // 业务规则验证是否启用
    private boolean businessRuleValidationEnabled = true;
    
    // 业务规则执行是否启用
    private boolean businessRuleExecutionEnabled = true;
    
    // 字段计算超时时间（毫秒）
    private long calculationTimeoutMs = 3000;
    
    // 业务规则注册表供应商
    private Supplier<BusinessRuleRegistry> businessRuleRegistrySupplier = DefaultBusinessRuleRegistry::new;
    
    // 配置构建器
    public static class Builder {
        private final EngineConfiguration configuration = new EngineConfiguration();
        
        public Builder withCachingEnabled(boolean enabled) {
            configuration.setEnableCaching(enabled);
            return this;
        }
        
        public Builder withAutoRefreshCache(boolean enabled) {
            configuration.setAutoRefreshCache(enabled);
            return this;
        }
        
        public Builder withCalculationEngineEnabled(boolean enabled) {
            configuration.setCalculationEngineEnabled(enabled);
            return this;
        }
        
        public Builder withBusinessRuleEngineEnabled(boolean enabled) {
            configuration.setBusinessRuleEngineEnabled(enabled);
            return this;
        }
        
        public Builder withValidationEngineEnabled(boolean enabled) {
            configuration.setValidationEngineEnabled(enabled);
            return this;
        }
        
        public Builder withIgnoreNullValidation(boolean enabled) {
            configuration.setIgnoreNullValidation(enabled);
            return this;
        }
        
        public Builder withMaxCalculationRecursionDepth(int depth) {
            configuration.setMaxCalculationRecursionDepth(depth);
            return this;
        }
        
        public Builder withRuleExecutionTimeoutMs(long timeout) {
            configuration.setRuleExecutionTimeoutMs(timeout);
            return this;
        }
        
        public Builder withMetadataRegistrySupplier(Supplier<MetadataRegistry> supplier) {
            configuration.setMetadataRegistrySupplier(supplier);
            return this;
        }
        
        public Builder withBusinessRuleValidationEnabled(boolean enabled) {
            configuration.setBusinessRuleValidationEnabled(enabled);
            return this;
        }
        
        public Builder withBusinessRuleExecutionEnabled(boolean enabled) {
            configuration.setBusinessRuleExecutionEnabled(enabled);
            return this;
        }
        
        public Builder withCalculationTimeoutMs(long timeout) {
            configuration.setCalculationTimeoutMs(timeout);
            return this;
        }
        
        public Builder withBusinessRuleRegistrySupplier(Supplier<BusinessRuleRegistry> supplier) {
            configuration.setBusinessRuleRegistrySupplier(supplier);
            return this;
        }
        
        public EngineConfiguration build() {
            return configuration;
        }
    }
    
    // 创建构建器的静态方法
    public static Builder builder() {
        return new Builder();
    }
    
    // 属性存储
    private final Map<String, Object> properties = new HashMap<>();
    
    // 构造函数
    public EngineConfiguration() {
        // 设置默认值
        setProperty("expression.cache.enabled", true);
        setProperty("expression.cache.size", 1000);
        setProperty("rule.execution.timeout", 3000);
        setProperty("validation.strict.mode", true);
        setProperty("debug.mode", false);
    }
    
    // 带属性的构造函数
    public EngineConfiguration(Map<String, Object> customProperties) {
        this();
        if (customProperties != null) {
            properties.putAll(customProperties);
        }
    }
    
    // 属性管理方法
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    public String getString(String key, String defaultValue) {
        Object value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }
    
    public int getInt(String key, int defaultValue) {
        Object value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
        } catch (NumberFormatException e) {
            // 忽略转换错误，返回默认值
        }
        return defaultValue;
    }
    
    public long getLong(String key, long defaultValue) {
        Object value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else if (value instanceof String) {
                return Long.parseLong((String) value);
            }
        } catch (NumberFormatException e) {
            // 忽略转换错误，返回默认值
        }
        return defaultValue;
    }
    
    public double getDouble(String key, double defaultValue) {
        Object value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        } catch (NumberFormatException e) {
            // 忽略转换错误，返回默认值
        }
        return defaultValue;
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            String strValue = ((String) value).toLowerCase();
            return "true".equals(strValue) || "on".equals(strValue) || "1".equals(strValue);
        }
        return defaultValue;
    }
    
    public boolean containsProperty(String key) {
        return properties.containsKey(key);
    }
    
    public void removeProperty(String key) {
        properties.remove(key);
    }
    
    public Map<String, Object> getAllProperties() {
        return new HashMap<>(properties);
    }
    
    public void setProperties(Map<String, Object> newProperties) {
        if (newProperties != null) {
            properties.putAll(newProperties);
        }
    }
    
    @Override
    public EngineConfiguration clone() {
        EngineConfiguration cloned = new EngineConfiguration();
        cloned.properties.putAll(this.properties);
        // 克隆其他字段
        cloned.enableCaching = this.enableCaching;
        cloned.autoRefreshCache = this.autoRefreshCache;
        cloned.calculationEngineEnabled = this.calculationEngineEnabled;
        cloned.businessRuleEngineEnabled = this.businessRuleEngineEnabled;
        cloned.validationEngineEnabled = this.validationEngineEnabled;
        cloned.ignoreNullValidation = this.ignoreNullValidation;
        cloned.maxCalculationRecursionDepth = this.maxCalculationRecursionDepth;
        cloned.ruleExecutionTimeoutMs = this.ruleExecutionTimeoutMs;
        cloned.metadataRegistrySupplier = this.metadataRegistrySupplier;
        cloned.businessRuleValidationEnabled = this.businessRuleValidationEnabled;
        cloned.businessRuleExecutionEnabled = this.businessRuleExecutionEnabled;
        cloned.calculationTimeoutMs = this.calculationTimeoutMs;
        cloned.businessRuleRegistrySupplier = this.businessRuleRegistrySupplier;
        return cloned;
    }
}