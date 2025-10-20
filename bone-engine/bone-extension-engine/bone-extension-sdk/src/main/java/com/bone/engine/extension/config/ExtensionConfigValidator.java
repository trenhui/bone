package com.bone.engine.extension.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 扩展点配置验证器，用于验证扩展点框架配置的有效性
 * <p>
 * 确保所有配置参数在合理范围内，防止无效配置导致的运行时问题
 * 
 * @author bone team
 */
@Component
public class ExtensionConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ExtensionConfigValidator.class);

    // 配置参数的有效范围
    private static final int MIN_CACHE_SIZE = 10;
    private static final int MAX_CACHE_SIZE = 10000;
    private static final long MIN_EXPIRE_TIME = 1;
    private static final long MAX_EXPIRE_TIME = 24 * 60 * 60; // 1秒到24小时

    /**
     * 验证扩展点配置的有效性
     * 
     * @param properties 扩展点配置属性对象
     * @throws IllegalArgumentException 当配置无效时抛出
     */
    public void validateConfig(ExtensionConfigProperties properties) {
        Assert.notNull(properties, "ExtensionConfigProperties must not be null");

        log.info("Validating extension point configuration...");
        List<String> validationErrors = validateConfigProperties(properties);

        if (!validationErrors.isEmpty()) {
            String errorMessage = "Extension point configuration validation failed. Errors: " +
                    String.join(", ", validationErrors);
            log.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        log.info("Extension point configuration validation passed.");
    }

    /**
     * 执行具体的配置验证逻辑，收集所有验证错误
     * 
     * @param properties 扩展点配置属性
     * @return 验证错误列表，如果为空表示验证通过
     */
    private List<String> validateConfigProperties(ExtensionConfigProperties properties) {
        List<String> errors = new ArrayList<>();

        // 验证缓存配置
        validateCacheConfig(properties, errors);
        
        // 验证过期时间配置
        validateExpireTime(properties, errors);
        
        // 验证其他配置
        validateOtherConfig(properties, errors);
        
        return errors;
    }

    private void validateCacheConfig(ExtensionConfigProperties properties, List<String> errors) {
        // 验证缓存大小配置
        int annotationCacheSize = properties.getAnnotationCacheMaxSize();
        if (annotationCacheSize < MIN_CACHE_SIZE || annotationCacheSize > MAX_CACHE_SIZE) {
            errors.add(String.format("Annotation cache size must be between %d and %d", MIN_CACHE_SIZE, MAX_CACHE_SIZE));
        }
        
        int routeCacheSize = properties.getRouteCacheMaxSize();
        if (routeCacheSize < MIN_CACHE_SIZE || routeCacheSize > MAX_CACHE_SIZE) {
            errors.add(String.format("Route cache size must be between %d and %d", MIN_CACHE_SIZE, MAX_CACHE_SIZE));
        }
    }
    
    private void validateExpireTime(ExtensionConfigProperties properties, List<String> errors) {
        long expireTime = properties.getCacheExpireAfterWrite();
        if (expireTime < MIN_EXPIRE_TIME || expireTime > MAX_EXPIRE_TIME) {
            errors.add(String.format("Cache expire time must be between %d and %d seconds", MIN_EXPIRE_TIME, MAX_EXPIRE_TIME));
        }
    }
    
    private void validateOtherConfig(ExtensionConfigProperties properties, List<String> errors) {
        // 验证基本配置
        if (properties.getScanPackages() == null || properties.getScanPackages().length == 0) {
            log.warn("No scan packages configured, using default scanning strategy");
        }
    }

    /**
     * 检查配置是否允许缓存
     * 
     * @param properties 扩展点配置属性
     * @return 是否启用缓存
     */
    /**
     * 检查配置是否允许缓存
     * 
     * @param properties 扩展点配置属性
     * @return 是否启用缓存
     */
    public boolean isCacheEnabled(ExtensionConfigProperties properties) {
        Assert.notNull(properties, "ExtensionConfigProperties must not be null");
        return properties.isCacheEnabled();
    }

    /**
     * 获取安全的注解缓存大小
     * 
     * @param properties 扩展点配置属性
     * @return 安全的缓存大小（确保在有效范围内）
     */
    public int getSafeAnnotationCacheSize(ExtensionConfigProperties properties) {
        Assert.notNull(properties, "ExtensionConfigProperties must not be null");
        int cacheSize = properties.getAnnotationCacheMaxSize();
        return Math.min(Math.max(cacheSize, MIN_CACHE_SIZE), MAX_CACHE_SIZE);
    }
    
    /**
     * 获取安全的路由缓存大小
     * 
     * @param properties 扩展点配置属性
     * @return 安全的缓存大小（确保在有效范围内）
     */
    public int getSafeRouteCacheSize(ExtensionConfigProperties properties) {
        Assert.notNull(properties, "ExtensionConfigProperties must not be null");
        int cacheSize = properties.getRouteCacheMaxSize();
        return Math.min(Math.max(cacheSize, MIN_CACHE_SIZE), MAX_CACHE_SIZE);
    }
}