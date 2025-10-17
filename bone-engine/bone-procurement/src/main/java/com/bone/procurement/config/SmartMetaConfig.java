package com.bone.procurement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * bone-smartmeta配置类
 * 配置元数据相关功能
 */
@Configuration
public class SmartMetaConfig {
    
    /**
     * 配置bone-smartmeta引擎参数
     * 在实际项目中，这里可以配置缓存大小、验证模式等参数
     */
    @Bean
    public SmartMetaProperties smartMetaProperties() {
        SmartMetaProperties properties = new SmartMetaProperties();
        properties.setCacheEnabled(true);
        properties.setValidationMode(ValidationMode.STRICT);
        properties.setEntityScanPackages("com.bone.procurement.entity");
        return properties;
    }
    
    /**
     * SmartMeta引擎属性配置类
     */
    public static class SmartMetaProperties {
        private boolean cacheEnabled = true;
        private ValidationMode validationMode = ValidationMode.STRICT;
        private String[] entityScanPackages = new String[0];
        
        // Getters and Setters
        public boolean isCacheEnabled() {
            return cacheEnabled;
        }
        
        public void setCacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
        }
        
        public ValidationMode getValidationMode() {
            return validationMode;
        }
        
        public void setValidationMode(ValidationMode validationMode) {
            this.validationMode = validationMode;
        }
        
        public String[] getEntityScanPackages() {
            return entityScanPackages;
        }
        
        public void setEntityScanPackages(String... entityScanPackages) {
            this.entityScanPackages = entityScanPackages;
        }
    }
    
    /**
     * 验证模式枚举
     */
    public enum ValidationMode {
        STRICT, // 严格模式，所有验证失败都会抛出异常
        WARNING // 警告模式，验证失败仅记录警告
    }
}