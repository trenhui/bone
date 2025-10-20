package com.bone.engine.extension.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 扩展点框架配置属性类，支持配置外部化管理
 * 遵循Spring Boot配置最佳实践，提供合理的默认值和类型安全的配置项
 */
@Component
@ConfigurationProperties(prefix = "bone.extension")
public class ExtensionConfigProperties {
    
    /**
     * 是否启用扩展点框架
     */
    private boolean enabled = true;
    
    /**
     * 是否启用版本管理
     */
    private boolean versioningEnabled = true;
    
    /**
     * 是否启用缓存
     */
    private boolean cacheEnabled = true;
    
    /**
     * 注解缓存的最大大小
     */
    private int annotationCacheMaxSize = 1000;
    
    /**
     * 路由结果缓存的最大大小
     */
    private int routeCacheMaxSize = 5000;
    
    /**
     * 缓存过期时间
     */
    private Duration cacheExpireAfterWrite = Duration.ofHours(1);
    
    /**
     * 缓存刷新时间
     */
    private Duration cacheRefreshAfterWrite = Duration.ofMinutes(30);
    
    /**
     * 是否启用异步事件处理
     */
    private boolean asyncEventEnabled = true;
    
    /**
     * 是否启用日志记录
     */
    private boolean loggingEnabled = true;
    
    /**
     * 是否启用性能指标收集
     */
    private boolean metricsEnabled = false;
    
    /**
     * 默认业务上下文租户ID
     */
    private String defaultTenantId = "DEFAULT";
    
    /**
     * 默认业务上下文业务类型
     */
    private String defaultBusinessType = "GENERAL";
    
    /**
     * 扩展点扫描路径配置
     */
    private String[] scanPackages = new String[]{};
    
    /**
     * 扩展点类型默认配置映射
     */
    private Map<String, String> defaultVersions = new HashMap<>();
    
    /**
     * 扩展点组件特定配置
     */
    private Map<String, Map<String, String>> componentProperties = new HashMap<>();
    
    /**
     * 扩展点调用超时时间（毫秒）
     */
    private long invocationTimeoutMillis = 5000;
    
    /**
     * 最大允许的匹配扩展数
     */
    private int maxMatchingExtensions = 10;
    
    /**
     * 是否启用扩展点的动态重载
     */
    private boolean dynamicReloadEnabled = false;
    
    /**
     * 动态重载扫描间隔（秒）
     */
    private int reloadScanIntervalSeconds = 60;
    
    /**
     * 是否启用扩展点安全检查
     */
    private boolean securityCheckEnabled = true;
    
    // Getters and Setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isVersioningEnabled() {
        return versioningEnabled;
    }
    
    public void setVersioningEnabled(boolean versioningEnabled) {
        this.versioningEnabled = versioningEnabled;
    }
    
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    
    
    public Duration getCacheExpireAfterWrite() {
        return cacheExpireAfterWrite;
    }
    
    public void setCacheExpireAfterWrite(Duration cacheExpireAfterWrite) {
        this.cacheExpireAfterWrite = cacheExpireAfterWrite;
    }
    
    public Duration getCacheRefreshAfterWrite() {
        return cacheRefreshAfterWrite;
    }
    
    public void setCacheRefreshAfterWrite(Duration cacheRefreshAfterWrite) {
        this.cacheRefreshAfterWrite = cacheRefreshAfterWrite;
    }
    
    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }
    
    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }
    
    public long getInvocationTimeoutMillis() {
        return invocationTimeoutMillis;
    }
    
    public void setInvocationTimeoutMillis(long invocationTimeoutMillis) {
        this.invocationTimeoutMillis = invocationTimeoutMillis;
    }
    
    public int getMaxMatchingExtensions() {
        return maxMatchingExtensions;
    }
    
    public void setMaxMatchingExtensions(int maxMatchingExtensions) {
        this.maxMatchingExtensions = maxMatchingExtensions;
    }
    
    public boolean isDynamicReloadEnabled() {
        return dynamicReloadEnabled;
    }
    
    public void setDynamicReloadEnabled(boolean dynamicReloadEnabled) {
        this.dynamicReloadEnabled = dynamicReloadEnabled;
    }
    
    public int getReloadScanIntervalSeconds() {
        return reloadScanIntervalSeconds;
    }
    
    public void setReloadScanIntervalSeconds(int reloadScanIntervalSeconds) {
        this.reloadScanIntervalSeconds = reloadScanIntervalSeconds;
    }
    
    public boolean isSecurityCheckEnabled() {
        return securityCheckEnabled;
    }
    
    public void setSecurityCheckEnabled(boolean securityCheckEnabled) {
        this.securityCheckEnabled = securityCheckEnabled;
    }
    
    // 兼容性方法：获取缓存过期时间（秒）
    public long getCacheExpireAfterWriteSeconds() {
        return cacheExpireAfterWrite.getSeconds();
    }
    
    // 兼容性方法：设置缓存过期时间（秒）
    public void setCacheExpireAfterWriteSeconds(long seconds) {
        this.cacheExpireAfterWrite = Duration.ofSeconds(seconds);
    }
    
    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }
    
    public int getAnnotationCacheMaxSize() {
        return annotationCacheMaxSize;
    }
    
    public void setAnnotationCacheMaxSize(int annotationCacheMaxSize) {
        this.annotationCacheMaxSize = annotationCacheMaxSize;
    }
    
    public int getRouteCacheMaxSize() {
        return routeCacheMaxSize;
    }
    
    public void setRouteCacheMaxSize(int routeCacheMaxSize) {
        this.routeCacheMaxSize = routeCacheMaxSize;
    }
    
    public void setCacheExpireAfterWrite(long seconds) {
        this.cacheExpireAfterWrite = Duration.ofSeconds(seconds);
    }
    
    public boolean isAsyncEventEnabled() {
        return asyncEventEnabled;
    }
    
    public void setAsyncEventEnabled(boolean asyncEventEnabled) {
        this.asyncEventEnabled = asyncEventEnabled;
    }
    
    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }
    
    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }
    
    public String getDefaultTenantId() {
        return defaultTenantId;
    }
    
    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }
    
    public String getDefaultBusinessType() {
        return defaultBusinessType;
    }
    
    public void setDefaultBusinessType(String defaultBusinessType) {
        this.defaultBusinessType = defaultBusinessType;
    }
    
    public String[] getScanPackages() {
        return scanPackages;
    }
    
    public void setScanPackages(String[] scanPackages) {
        this.scanPackages = scanPackages;
    }
    
    public Map<String, String> getDefaultVersions() {
        return defaultVersions;
    }
    
    public void setDefaultVersions(Map<String, String> defaultVersions) {
        this.defaultVersions = defaultVersions;
    }
    
    public Map<String, Map<String, String>> getComponentProperties() {
        return componentProperties;
    }
    
    public void setComponentProperties(Map<String, Map<String, String>> componentProperties) {
        this.componentProperties = componentProperties;
    }
    
    /**
     * 获取特定组件的配置属性
     * 
     * @param componentName 组件名称
     * @return 组件配置属性，如果不存在则返回空Map
     */
    public Map<String, String> getComponentProperty(String componentName) {
        return componentProperties.getOrDefault(componentName, new HashMap<>());
    }
    
    /**
     * 获取特定组件的特定配置值
     * 
     * @param componentName 组件名称
     * @param propertyName 属性名称
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public String getComponentProperty(String componentName, String propertyName, String defaultValue) {
        Map<String, String> props = getComponentProperty(componentName);
        return props.getOrDefault(propertyName, defaultValue);
    }
}