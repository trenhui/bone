package com.bone.engine.extension.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 扩展点路由配置更新器
 * <p>
 * 根据元数据信息自动更新路由配置，支持优先级、默认实现等特性
 * </p>
 * 
 * @since 1.0.0
 */
@Component
public class ExtPointRoutingConfigUpdater {
    
    private static final Logger log = Logger.getLogger(ExtPointRoutingConfigUpdater.class.getName());
    
    @Autowired
    private ExtPointRoutingConfigManager configManager;
    
    @Autowired
    private ExtPointMetadataCollector metadataCollector;
    
    /**
     * 根据元数据信息更新路由配置
     * 
     * @param interfaceName 扩展点接口名
     * @return 更新的配置数量
     */
    public int updateRoutingConfigByMetadata(String interfaceName) {
        ExtPointMetadata extPointMetadata = metadataCollector.getExtPointMetadata(interfaceName);
        if (extPointMetadata == null) {
            log.warning("ExtPoint metadata not found for: " + interfaceName);
            return 0;
        }
        
        int updatedCount = 0;
        
        // 处理每个实现的路由配置
        List<ExtensionImplMetadata> implementations = extPointMetadata.getImplementations();
        if (implementations != null) {
            for (ExtensionImplMetadata impl : implementations) {
                Map<String, String> routingConfig = new HashMap<>();
                routingConfig.put("priority", "0");
                routingConfig.put("isDefault", "false");
                routingConfig.put("recommended", "false");
                configManager.updateRoutingConfig(interfaceName, "unknown", routingConfig);
                updatedCount++;
            }
        }
        
        log.info("Updated routing configs for " + updatedCount + " implementations of " + interfaceName);
        return updatedCount;
    }
    
    /**
     * 从元数据构建路由配置
     */
    private Map<String, String> buildRoutingConfigFromMetadata(ExtImplMetadata implMetadata) {
        Map<String, String> config = new HashMap<>();
        
        // 设置优先级
        if (implMetadata.getPriority() != 0) {
            config.put("priority", String.valueOf(implMetadata.getPriority()));
        }
        
        // 设置是否为默认实现
        if (implMetadata.isDefaultImpl()) {
            config.put("isDefault", "true");
        }
        
        // 设置是否推荐
        if (implMetadata.isRecommended()) {
            config.put("recommended", "true");
        }
        
        // 添加自定义路由条件
        if (implMetadata.getRoutingConditions() != null && !implMetadata.getRoutingConditions().isEmpty()) {
            config.putAll(implMetadata.getRoutingConditions());
        }
        
        return config;
    }
    
    /**
     * 更新所有扩展点的路由配置
     * 
     * @return 更新的总配置数量
     */
    public int updateAllRoutingConfigs() {
        int totalUpdated = 0;
        
        Map<String, ExtPointMetadata> allMetadata = metadataCollector.getAllExtPointMetadata();
        for (String interfaceName : allMetadata.keySet()) {
            totalUpdated += updateRoutingConfigByMetadata(interfaceName);
        }
        
        log.info("Updated all routing configs, total updated: " + totalUpdated);
        return totalUpdated;
    }
}