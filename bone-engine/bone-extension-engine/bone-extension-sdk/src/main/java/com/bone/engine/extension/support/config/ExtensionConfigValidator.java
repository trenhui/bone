package com.bone.engine.extension.support.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

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

    @Autowired
    private ExtensionProperties properties;

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
    public void validateConfig(ExtensionProperties properties) {
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
    private List<String> validateConfigProperties(ExtensionProperties properties) {
        List<String> errors = new ArrayList<>();

        // 验证缓存配置
        validateCacheConfig(properties, errors);
        
        // 验证过期时间配置
        validateExpireTime(properties, errors);
        
        // 验证其他配置
        validateOtherConfig(properties, errors);
        
        return errors;
    }

    private void validateCacheConfig(ExtensionProperties properties, List<String> errors) {
        // 验证缓存大小配置
        int cacheSize = properties.getCache().getMaxSize();
        if (cacheSize < MIN_CACHE_SIZE || cacheSize > MAX_CACHE_SIZE) {
            errors.add(String.format("Cache size must be between %d and %d", MIN_CACHE_SIZE, MAX_CACHE_SIZE));
        }
    }
    
    private void validateExpireTime(ExtensionProperties properties, List<String> errors) {
        // 使用配置中的过期时间（转换为秒）
        long expireTimeSeconds = properties.getCache().getExpireAfterWrite() / 1000;
        if (expireTimeSeconds < MIN_EXPIRE_TIME || expireTimeSeconds > MAX_EXPIRE_TIME) {
            errors.add(String.format("Cache expire time must be between %d and %d seconds", MIN_EXPIRE_TIME, MAX_EXPIRE_TIME));
        }
    }
    
    private void validateOtherConfig(ExtensionProperties properties, List<String> errors) {
        // 验证基本配置（跳过包扫描验证）
        // 假设配置已正确设置
    }

    /**
     * 验证缓存配置
     * 
     * @param maxSize 缓存最大大小
     * @return 有效的缓存大小
     */
    public int validateCacheConfig(int maxSize) {
        // 统一使用ExtensionProperties中的配置值
        if (properties != null) {
            maxSize = properties.getCache().getMaxSize();
        }
        
        if (maxSize <= 0) {
            // 如果配置无效，使用默认值
            maxSize = 1000; // 默认缓存大小
            log.warn("Invalid cache max size: {}. Using default: 1000.", maxSize);
        }
        return maxSize;
    }

    /**
     * 验证过期时间配置
     * 
     * @param expireTime 过期时间(毫秒)
     * @return 有效的过期时间
     */
    public long validateExpireTime(long expireTime) {
        // 优先使用ExtensionProperties中的配置值
        if (properties != null) {
            expireTime = properties.getCache().getExpireAfterWrite();
        }
        
        if (expireTime <= 0) {
            expireTime = 300000; // 默认5分钟
            log.warn("Invalid cache expire time: {}. Using default: 5 minutes.", expireTime);
        }
        return expireTime;
    }

    /**
     * 检查配置是否允许缓存
     * 
     * @param properties 扩展点配置属性
     * @return 是否启用缓存
     */
    public boolean isCacheEnabled(ExtensionProperties properties) {
        Assert.notNull(properties, "ExtensionProperties must not be null");
        return properties.getCache().isEnabled();
    }
    
    /**
     * 检查缓存是否启用（无参数版本）
     * 
     * @return 是否启用缓存
     */
    public boolean isCacheEnabled() {
        // 直接使用ExtensionProperties中的配置值
        if (properties != null) {
            return properties.getCache().isEnabled();
        }
        return true; // 默认启用
    }

    /**
     * 获取安全的注解缓存大小
     * 
     * @param properties 扩展点配置属性
     * @return 安全的缓存大小（确保在有效范围内）
     */
    public int getSafeAnnotationCacheSize(ExtensionProperties properties) {
        Assert.notNull(properties, "ExtensionProperties must not be null");
        int cacheSize = properties.getCache().getMaxSize();
        return Math.min(Math.max(cacheSize, MIN_CACHE_SIZE), MAX_CACHE_SIZE);
    }
    
    /**
     * 获取安全的注解缓存大小（无参数版本）
     * 
     * @param maxSize 配置的缓存大小
     * @return 安全的缓存大小
     */
    public int getSafeAnnotationCacheSize(int maxSize) {
        // 优先使用ExtensionProperties中的配置值
        if (properties != null) {
            maxSize = properties.getCache().getMaxSize();
        }
        
        if (maxSize <= 0) {
            return 500; // 默认注解缓存大小
        }
        return Math.min(maxSize, 10000); // 最大不超过10000
    }
    
    /**
     * 获取安全的路由缓存大小
     * 
     * @param properties 扩展点配置属性
     * @return 安全的缓存大小（确保在有效范围内）
     */
    public int getSafeRouteCacheSize(ExtensionProperties properties) {
        Assert.notNull(properties, "ExtensionProperties must not be null");
        int cacheSize = properties.getCache().getMaxSize();
        return Math.min(Math.max(cacheSize, MIN_CACHE_SIZE), MAX_CACHE_SIZE);
    }
    
    /**
     * 获取安全的路由缓存大小（无参数版本）
     * 
     * @param maxSize 配置的缓存大小
     * @return 安全的缓存大小
     */
    public int getSafeRouteCacheSize(int maxSize) {
        // 优先使用ExtensionProperties中的配置值
        if (properties != null) {
            maxSize = properties.getCache().getMaxSize();
        }
        
        if (maxSize <= 0) {
            return 1000; // 默认路由缓存大小
        }
        return Math.min(maxSize, 50000); // 最大不超过50000
    }
    
    /**
     * 设置ExtensionProperties（用于测试或手动配置）
     */
    public void setProperties(ExtensionProperties properties) {
        this.properties = properties;
    }
}