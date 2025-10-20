package com.bone.engine.extension.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 扩展点路由配置管理器
 * <p>
 * 负责管理和更新扩展实现的路由配置，支持运行时配置更新
 * </p>
 * 
 * @since 1.0.0
 */
@Component
public class ExtPointRoutingConfigManager {
    
    private static final Logger log = Logger.getLogger(ExtPointRoutingConfigManager.class.getName());
    
    // 路由配置缓存
    private final Map<String, Map<String, String>> routingConfigCache = new ConcurrentHashMap<>();
    
    /**
     * 更新扩展实现的路由配置
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @param config 新的路由配置
     * @return 是否更新成功
     */
    public boolean updateRoutingConfig(String interfaceName, String implClassName, Map<String, String> config) {
        if (!StringUtils.hasText(interfaceName) || !StringUtils.hasText(implClassName) || config == null) {
            log.warning("Invalid parameters for updating routing config");
            return false;
        }
        
        String cacheKey = buildCacheKey(interfaceName, implClassName);
        
        // 创建新的配置副本
        Map<String, String> newConfig = new ConcurrentHashMap<>(config);
        routingConfigCache.put(cacheKey, newConfig);
        
        log.info("Updated routing config for " + interfaceName + "#" + implClassName + ": " + config);
        return true;
    }
    
    /**
     * 获取扩展实现的路由配置
     * 
     * @param interfaceName 扩展点接口名
     * @param implClassName 实现类名
     * @return 路由配置，如果不存在返回null
     */
    public Map<String, String> getRoutingConfig(String interfaceName, String implClassName) {
        String cacheKey = buildCacheKey(interfaceName, implClassName);
        return routingConfigCache.get(cacheKey);
    }
    
    /**
     * 构建缓存键
     */
    private String buildCacheKey(String interfaceName, String implClassName) {
        return interfaceName + "#" + implClassName;
    }
    
    /**
     * 清除指定扩展实现的路由配置
     */
    public void clearRoutingConfig(String interfaceName, String implClassName) {
        String cacheKey = buildCacheKey(interfaceName, implClassName);
        routingConfigCache.remove(cacheKey);
        log.info("Cleared routing config for " + interfaceName + "#" + implClassName);
    }
    
    /**
     * 清除所有路由配置缓存
     */
    public void clearAll() {
        routingConfigCache.clear();
        log.info("Cleared all routing configs");
    }
}