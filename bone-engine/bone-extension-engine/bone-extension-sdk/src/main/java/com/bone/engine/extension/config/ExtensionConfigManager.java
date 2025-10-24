package com.bone.engine.extension.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展点配置管理器
 * <p>
 * 负责管理扩展点的全局配置，包括缓存设置、路由规则、扩展点开关等
 * <strong>主要功能：</strong>
 * <ul>
 *   <li>加载和管理扩展点配置</li>
 *   <li>提供配置访问接口</li>
 *   <li>支持动态配置更新</li>
 *   <li>配置缓存管理</li>
 * </ul>
 * <strong>注意：此为适配层，内部使用ExtensionProperties进行实际配置管理</strong>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class ExtensionConfigManager {

    private static final String PROPERTY_PREFIX = "bone.extension.";
    
    // 配置缓存
    private final Map<String, String> configCache = new ConcurrentHashMap<>();
    
    // 扩展点特定配置
    private final Map<String, Map<String, String>> extPointConfigs = new ConcurrentHashMap<>();
    
    @Autowired
    private ExtensionProperties extensionProperties;

    /**
     * 初始化配置管理器
     */
    public void init() {
        // 作为适配层，初始化缓存以保持向后兼容性
        if (extensionProperties != null) {
            // 初始化全局配置缓存
            configCache.put(PROPERTY_PREFIX + "cache.enabled", String.valueOf(extensionProperties.getCache().isEnabled()));
            configCache.put(PROPERTY_PREFIX + "cache.expire-time", String.valueOf(extensionProperties.getCache().getExpireTime()));
            
            log.info("Initialized ExtensionConfigManager with ExtensionProperties");
        }
    }

    /**
     * 获取全局配置值
     */
    public String getGlobalConfig(String configKey) {
        // 优先从ExtensionProperties获取配置
        if (extensionProperties != null && configKey.equals("cache.enabled")) {
            return String.valueOf(extensionProperties.getCache().isEnabled());
        } else if (extensionProperties != null && configKey.equals("cache.expire-time")) {
            return String.valueOf(extensionProperties.getCache().getExpireTime());
        }
        
        // 向后兼容
        return configCache.get(PROPERTY_PREFIX + configKey);
    }

    /**
     * 获取全局配置的布尔值
     */
    public boolean getGlobalConfigBoolean(String configKey, boolean defaultValue) {
        String value = getGlobalConfig(configKey);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * 获取扩展点特定配置
     */
    public String getExtPointConfig(String extPointName, String configKey) {
        Map<String, String> configs = extPointConfigs.get(extPointName);
        return configs != null ? configs.get(configKey) : null;
    }

    /**
     * 获取扩展点特定配置的布尔值
     */
    public boolean getExtPointConfigBoolean(String extPointName, String configKey, boolean defaultValue) {
        String value = getExtPointConfig(extPointName, configKey);
        return StringUtils.hasText(value) ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * 设置扩展点配置（运行时更新）
     */
    public void setExtPointConfig(String extPointName, String configKey, String value) {
        String fullKey = PROPERTY_PREFIX + extPointName + "." + configKey;
        configCache.put(fullKey, value);
        
        extPointConfigs.computeIfAbsent(extPointName, k -> new HashMap<>())
                .put(configKey, value);
    }

    /**
     * 检查扩展点是否启用
     */
    public boolean isExtPointEnabled(String extPointName) {
        return getExtPointConfigBoolean(extPointName, "enabled", true);
    }

    /**
     * 检查扩展点是否启用缓存
     */
    public boolean isExtPointCacheEnabled(String extPointName) {
        // 优先从ExtensionProperties获取配置
        if (extensionProperties != null) {
            return extensionProperties.getCache().isEnabled();
        }
        // 向后兼容
        return getExtPointConfigBoolean(extPointName, "cache.enabled", 
                getGlobalConfigBoolean("cache.enabled", true));
    }

    /**
     * 获取扩展点的缓存过期时间（毫秒）
     */
    public long getExtPointCacheExpireTime(String extPointName) {
        // 优先从ExtensionProperties获取配置
        if (extensionProperties != null) {
            return extensionProperties.getCache().getExpireTime();
        }
        // 向后兼容
        String value = getExtPointConfig(extPointName, "cache.expire-time");
        if (StringUtils.hasText(value)) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // 忽略格式错误
            }
        }
        return 300000; // 默认5分钟
    }

    /**
     * 清理配置缓存
     */
    public void clearCache() {
        configCache.clear();
        extPointConfigs.clear();
        init(); // 重新初始化缓存
    }

    /**
     * 获取所有配置项
     */
    public Map<String, String> getAllConfigs() {
        return new HashMap<>(configCache);
    }
    
    /**
     * 设置ExtensionProperties（用于测试或手动配置）
     */
    public void setExtensionProperties(ExtensionProperties extensionProperties) {
        this.extensionProperties = extensionProperties;
        init();
    }
}