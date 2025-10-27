package com.bone.engine.extension.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展点配置管理器
 * <p>
 * 负责管理扩展点的全局配置，包括缓存设置、路由规则、扩展点开关等
 * <strong>主要功能：</strong>
 * <ul>
 *   <li>提供统一的配置访问接口</li>
 *   <li>支持扩展点特定配置</li>
 *   <li>作为ExtensionProperties的适配层</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
@Component
public class ExtensionConfigManager {
    private static final Logger log = LoggerFactory.getLogger(ExtensionConfigManager.class);
    
    // 扩展点特定配置（非全局配置，仅用于单个扩展点）
    private final Map<String, Map<String, String>> extPointConfigs = new ConcurrentHashMap<>();
    
    @Autowired
    private ExtensionProperties extensionProperties;

    /**
     * 初始化配置管理器
     */
    public void init() {
        log.info("Initialized ExtensionConfigManager with ExtensionProperties");
    }

    /**
     * 获取全局配置值
     * <p>直接从ExtensionProperties获取配置，提供统一的访问接口</p>
     */
    public String getGlobalConfig(String configKey) {
        // 直接从ExtensionProperties获取配置
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
        
        // 不再从缓存获取，直接返回null
        return null;
    }

    /**
     * 获取布尔类型的全局配置值
     * <p>直接从ExtensionProperties获取配置，提供类型转换</p>
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
    /**
     * 清除扩展点特定配置缓存
     * （全局配置直接从ExtensionProperties获取，无需缓存）
     */
    public void clearCache() {
        extPointConfigs.clear();
        log.debug("Extension point specific configs cleared");
    }

    /**
     * 获取所有配置项
     * 注意：此方法仅返回扩展点特定配置，全局配置应通过getGlobalConfig方法获取
     *
     * @return 所有配置项的Map
     */
    public Map<String, String> getAllConfigs() {
        // 全局配置不再缓存，此方法仅保留向后兼容性
        return Map.of();
    }
    
    /**
     * 设置ExtensionProperties（用于测试或手动配置）
     */
    public void setExtensionProperties(ExtensionProperties extensionProperties) {
        this.extensionProperties = extensionProperties;
        init();
    }
}