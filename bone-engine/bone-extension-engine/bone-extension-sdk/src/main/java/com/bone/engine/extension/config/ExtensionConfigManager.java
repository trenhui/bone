package com.bone.engine.extension.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Component
public class ExtensionConfigManager {
    private static final Logger log = LoggerFactory.getLogger(ExtensionConfigManager.class);
    
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
            // 初始化全局配置缓存 - 缓存配置
            configCache.put(PROPERTY_PREFIX + "cache.enabled", String.valueOf(extensionProperties.getCache().isEnabled()));
            configCache.put(PROPERTY_PREFIX + "cache.expire-time", String.valueOf(extensionProperties.getCache().getExpireTime()));
            configCache.put(PROPERTY_PREFIX + "cache.max-size", String.valueOf(extensionProperties.getCache().getMaxSize()));
            
            // 扫描配置
            configCache.put(PROPERTY_PREFIX + "scan.enabled", String.valueOf(extensionProperties.getScan().isEnabled()));
            configCache.put(PROPERTY_PREFIX + "scan.auto-register", String.valueOf(extensionProperties.getScan().isAutoRegister()));
            
            // 事件配置
            configCache.put(PROPERTY_PREFIX + "events.enabled", String.valueOf(extensionProperties.getEvents().isEnabled()));
            configCache.put(PROPERTY_PREFIX + "events.async", String.valueOf(extensionProperties.getEvents().isAsync()));
            configCache.put(PROPERTY_PREFIX + "events.executor", extensionProperties.getEvents().getExecutor());
            
            log.info("Initialized ExtensionConfigManager with ExtensionProperties");
        }
    }

    /**
     * 获取全局配置值
     * <p>采用一致的配置访问模式，优先从ExtensionProperties获取，其次从缓存获取</p>
     */
    public String getGlobalConfig(String configKey) {
        // 优先从ExtensionProperties获取配置 - 使用统一的命名映射
        if (extensionProperties != null) {
            // 缓存配置
            if (configKey.equals("cache.enabled")) {
                return String.valueOf(extensionProperties.getCache().isEnabled());
            } else if (configKey.equals("cache.expire-time")) {
                return String.valueOf(extensionProperties.getCache().getExpireTime());
            } else if (configKey.equals("cache.max-size")) {
                return String.valueOf(extensionProperties.getCache().getMaxSize());
            }
            // 扫描配置
            else if (configKey.equals("scan.enabled")) {
                return String.valueOf(extensionProperties.getScan().isEnabled());
            } else if (configKey.equals("scan.auto-register")) {
                return String.valueOf(extensionProperties.getScan().isAutoRegister());
            }
            // 事件配置
            else if (configKey.equals("events.enabled")) {
                return String.valueOf(extensionProperties.getEvents().isEnabled());
            } else if (configKey.equals("events.async")) {
                return String.valueOf(extensionProperties.getEvents().isAsync());
            } else if (configKey.equals("events.executor")) {
                return extensionProperties.getEvents().getExecutor();
            }
        }
        
        // 向后兼容 - 从缓存获取
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
        // 优先检查扩展点特定配置，其次使用全局配置
        return getExtPointConfigBoolean(extPointName, "cache.enabled", 
                getGlobalConfigBoolean("cache.enabled", true));
    }

    /**
     * 获取扩展点的缓存过期时间（毫秒）
     */
    public long getExtPointCacheExpireTime(String extPointName) {
        // 优先检查扩展点特定配置
        String value = getExtPointConfig(extPointName, "cache.expire-time");
        if (StringUtils.hasText(value)) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid cache expire time format for extPoint {}: {}", extPointName, value);
            }
        }
        
        // 其次使用全局配置
        value = getGlobalConfig("cache.expire-time");
        if (StringUtils.hasText(value)) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid global cache expire time format: {}", value);
            }
        }
        
        // 默认值
        return 300000; // 默认5分钟
    }
    
    /**
     * 获取扩展点的缓存最大大小
     */
    public int getExtPointCacheMaxSize(String extPointName) {
        // 优先检查扩展点特定配置
        String value = getExtPointConfig(extPointName, "cache.max-size");
        if (StringUtils.hasText(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid cache max size format for extPoint {}: {}", extPointName, value);
            }
        }
        
        // 其次使用全局配置
        value = getGlobalConfig("cache.max-size");
        if (StringUtils.hasText(value)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid global cache max size format: {}", value);
            }
        }
        
        // 默认值
        return 1000; // 默认1000个条目
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