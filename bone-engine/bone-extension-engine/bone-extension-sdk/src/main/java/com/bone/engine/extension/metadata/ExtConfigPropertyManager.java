package com.bone.engine.extension.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.bone.engine.extension.metadata.example.ValidationResult;

/**
 * 扩展配置属性管理器
 * <p>
 * 负责管理、验证和提供扩展实现的配置属性
 * </p>
 * 
 * @since 1.0.0
 */
@Component
public class ExtConfigPropertyManager {
    
    private static final Logger log = Logger.getLogger(ExtConfigPropertyManager.class.getName());
    
    // 配置属性缓存
    private final Map<String, Map<String, String>> propertyCache = new HashMap<>();
    
    // 配置属性验证规则缓存
    private final Map<String, Map<String, PropertyRule>> validationRuleCache = new HashMap<>();
    
    /**
     * 注册扩展实现的配置属性
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @param properties 配置属性
     */
    public void registerProperties(String interfaceName, String implClassName, Map<String, String> properties) {
        if (StringUtils.hasText(interfaceName) && StringUtils.hasText(implClassName) && properties != null) {
            String cacheKey = buildCacheKey(interfaceName, implClassName);
            propertyCache.put(cacheKey, new HashMap<>(properties));
            log.info("Registered properties for " + interfaceName + "#" + implClassName);
        }
    }
    
    /**
     * 获取扩展实现的配置属性
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @return 配置属性映射
     */
    public Map<String, String> getProperties(String interfaceName, String implClassName) {
        String cacheKey = buildCacheKey(interfaceName, implClassName);
        Map<String, String> properties = propertyCache.get(cacheKey);
        return properties != null ? new HashMap<>(properties) : new HashMap<>();
    }
    
    /**
     * 获取单个配置属性值
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @param propertyName 属性名
     * @return 属性值，如果不存在返回null
     */
    public String getProperty(String interfaceName, String implClassName, String propertyName) {
        Map<String, String> properties = getProperties(interfaceName, implClassName);
        return properties.get(propertyName);
    }
    
    /**
     * 更新配置属性
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @param propertyName 属性名
     * @param propertyValue 属性值
     * @return 验证结果
     */
    public ValidationResult updateProperty(String interfaceName, String implClassName, 
                                          String propertyName, String propertyValue) {
        ValidationResult result = new ValidationResult();
        
        // 验证属性
        result = validateProperty(interfaceName, implClassName, propertyName, propertyValue);
        if (!result.isValid()) {
            return result;
        }
        
        // 更新属性
        String cacheKey = buildCacheKey(interfaceName, implClassName);
        Map<String, String> properties = propertyCache.computeIfAbsent(cacheKey, k -> new HashMap<>());
        properties.put(propertyName, propertyValue);
        
        log.info("Updated property " + propertyName + "=" + propertyValue + " for " + interfaceName + "#" + implClassName);
        result.setValid(true);
        return result;
    }
    
    /**
     * 验证配置属性
     */
    private ValidationResult validateProperty(String interfaceName, String implClassName, 
                                             String propertyName, String propertyValue) {
        ValidationResult result = new ValidationResult();
        
        // 检查必填性
        Map<String, PropertyRule> rules = getValidationRules(interfaceName, implClassName);
        PropertyRule rule = rules.get(propertyName);
        
        if (rule != null) {
            // 必填检查
            if (rule.required && !StringUtils.hasText(propertyValue)) {
                result.setValid(false);
                result.addErrorMessage("Property '" + propertyName + "' is required");
                return result;
            }
            
            // 类型检查
            if (StringUtils.hasText(propertyValue)) {
                if (!validateByType(propertyValue, rule.type)) {
                    result.setValid(false);
                    result.addErrorMessage("Property '" + propertyName + "' must be of type " + rule.type);
                    return result;
                }
                
                // 格式检查
                if (StringUtils.hasText(rule.pattern) && !validateByPattern(propertyValue, rule.pattern)) {
                    result.setValid(false);
                    result.addErrorMessage("Property '" + propertyName + "' does not match pattern " + rule.pattern);
                    return result;
                }
            }
        }
        
        result.setValid(true);
        return result;
    }
    
    /**
     * 根据类型验证属性值
     */
    private boolean validateByType(String value, String type) {
        try {
            switch (type.toLowerCase()) {
                case "integer":
                    Integer.parseInt(value);
                    return true;
                case "long":
                    Long.parseLong(value);
                    return true;
                case "double":
                    Double.parseDouble(value);
                    return true;
                case "boolean":
                    Boolean.parseBoolean(value);
                    return true;
                case "string":
                    return true;
                default:
                    return true; // 未知类型，默认为有效
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 根据正则表达式验证属性值
     */
    private boolean validateByPattern(String value, String pattern) {
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(value);
            return m.matches();
        } catch (Exception e) {
            log.warning("Invalid validation pattern: " + pattern);
            return true; // 正则表达式无效，默认为有效
        }
    }
    
    /**
     * 注册验证规则
     */
    public void registerValidationRules(String interfaceName, String implClassName, 
                                      Map<String, PropertyRule> rules) {
        String cacheKey = buildCacheKey(interfaceName, implClassName);
        validationRuleCache.put(cacheKey, new HashMap<>(rules));
        log.info("Registered validation rules for " + interfaceName + "#" + implClassName);
    }
    
    /**
     * 获取验证规则
     */
    private Map<String, PropertyRule> getValidationRules(String interfaceName, String implClassName) {
        String cacheKey = buildCacheKey(interfaceName, implClassName);
        Map<String, PropertyRule> rules = validationRuleCache.get(cacheKey);
        return rules != null ? rules : new HashMap<>();
    }
    
    /**
     * 构建缓存键
     */
    private String buildCacheKey(String interfaceName, String implClassName) {
        return interfaceName + "#" + implClassName;
    }
    
    /**
     * 清除缓存
     */
    public void clearCache(String interfaceName, String implClassName) {
        String cacheKey = buildCacheKey(interfaceName, implClassName);
        propertyCache.remove(cacheKey);
        validationRuleCache.remove(cacheKey);
        log.info("Cleared cache for " + interfaceName + "#" + implClassName);
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        propertyCache.clear();
        validationRuleCache.clear();
        log.info("Cleared all cache");
    }
    
    /**
     * 属性规则类
     */
    public static class PropertyRule {
        private boolean required = false;
        private String type = "string";
        private String pattern;
        private String description;
        private String defaultValue;
        
        // Getters and Setters
        public boolean isRequired() {
            return required;
        }
        public void setRequired(boolean required) {
            this.required = required;
        }
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        public String getPattern() {
            return pattern;
        }
        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
        public String getDescription() {
            return description;
        }
        public void setDescription(String description) {
            this.description = description;
        }
        public String getDefaultValue() {
            return defaultValue;
        }
        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
    }
}