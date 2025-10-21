package com.bone.engine.extension.config;

import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
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
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public class ExtensionConfigManager {

    private static final String DEFAULT_CONFIG_LOCATION = "classpath:extension.properties";
    private static final String PROPERTY_PREFIX = "bone.extension.";
    
    // 配置缓存
    private final Map<String, String> configCache = new ConcurrentHashMap<>();
    
    // 扩展点特定配置
    private final Map<String, Map<String, String>> extPointConfigs = new ConcurrentHashMap<>();

    /**
     * 初始化配置管理器
     */
    public void init() {
        try {
            // 加载默认配置文件
            Properties properties = PropertiesLoaderUtils.loadAllProperties(DEFAULT_CONFIG_LOCATION);
            loadProperties(properties);
        } catch (IOException e) {
            // 默认配置文件不存在时，使用默认配置
        }
    }

    /**
     * 加载配置属性
     */
    public void loadProperties(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(PROPERTY_PREFIX)) {
                String value = properties.getProperty(key);
                configCache.put(key, value);
                
                // 解析扩展点特定配置
                parseExtPointConfig(key, value);
            }
        }
    }

    private void parseExtPointConfig(String key, String value) {
        // 格式: bone.extension.[extPointName].[configKey]=value
        String[] parts = key.substring(PROPERTY_PREFIX.length()).split("\\.", 2);
        if (parts.length == 2) {
            String extPointName = parts[0];
            String configKey = parts[1];
            
            extPointConfigs.computeIfAbsent(extPointName, k -> new HashMap<>())
                    .put(configKey, value);
        }
    }

    /**
     * 获取全局配置值
     */
    public String getGlobalConfig(String configKey) {
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
        return getExtPointConfigBoolean(extPointName, "cache.enabled", 
                getGlobalConfigBoolean("cache.enabled", true));
    }

    /**
     * 获取扩展点的缓存过期时间（毫秒）
     */
    public long getExtPointCacheExpireTime(String extPointName) {
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
    }

    /**
     * 获取所有配置项
     */
    public Map<String, String> getAllConfigs() {
        return new HashMap<>(configCache);
    }
}