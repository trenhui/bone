package com.bone.engine.extension.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 扩展点元数据导出器
 * <p>
 * 负责将扩展点和扩展实现的元数据导出为各种格式
 * </p>
 * 
 * @since 1.0.0
 */
@Component
public class ExtPointMetadataExporter {
    
    private static final Logger log = Logger.getLogger(ExtPointMetadataExporter.class.getName());
    
    @Autowired
    private ExtPointMetadataCollector metadataCollector;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 导出指定扩展点的元数据为JSON格式
     * 
     * @param interfaceName 扩展点接口名
     * @return JSON字符串
     */
    public String exportAsJson(String interfaceName) {
        ExtPointMetadata metadata = metadataCollector.getExtPointMetadata(interfaceName);
        if (metadata == null) {
            log.warning("Metadata not found for interface: " + interfaceName);
            return "{}";
        }
        
        try {
            return objectMapper.writeValueAsString(convertToExportFormat(metadata));
        } catch (IOException e) {
            log.severe("Failed to export metadata to JSON for " + interfaceName + ": " + e.getMessage());
            return "{}";
        }
    }
    
    /**
     * 导出所有扩展点的元数据为JSON格式
     * 
     * @return 包含所有扩展点元数据的JSON字符串
     */
    public String exportAllAsJson() {
        Map<String, Object> allMetadata = exportAllMetadata();
        
        try {
            return objectMapper.writeValueAsString(allMetadata);
        } catch (IOException e) {
            log.severe("Failed to export all metadata to JSON: " + e.getMessage());
            return "{}";
        }
    }
    
    /**
     * 导出元数据摘要为JSON格式（无参数版本）
     */
    public String exportAsJson() {
        return exportAsSummaryJson();
    }
    
    /**
     * 导出所有扩展点元数据为Map格式
     */
    public Map<String, Object> exportAllMetadata() {
        Map<String, Object> result = new HashMap<>();
        Map<String, ExtPointMetadata> allExtPointMetadata = metadataCollector.getAllExtPointMetadata();
        
        Map<String, ExtPointMetadata> extPoints = new HashMap<>();
        for (Map.Entry<String, ExtPointMetadata> entry : allExtPointMetadata.entrySet()) {
            extPoints.put(entry.getKey(), entry.getValue());
        }
        
        result.put("extPoints", extPoints);
        result.put("totalExtPoints", allExtPointMetadata.size());
        
        return result;
    }
    
    /**
     * 导出扩展点元数据为Map格式
     * 
     * @param interfaceName 扩展点接口名
     * @return 元数据Map
     */
    public Map<String, Object> exportAsMap(String interfaceName) {
        ExtPointMetadata metadata = metadataCollector.getExtPointMetadata(interfaceName);
        if (metadata == null) {
            log.warning("Metadata not found for interface: " + interfaceName);
            return new HashMap<>();
        }
        
        return convertToExportFormat(metadata);
    }
    
    /**
     * 将元数据转换为导出格式
     */
    private Map<String, Object> convertToExportFormat(ExtPointMetadata metadata) {
        Map<String, Object> exportData = new HashMap<>();
        
        // 扩展点基本信息
        exportData.put("interfaceName", metadata.getInterfaceName());
        exportData.put("description", metadata.getDescription());
        exportData.put("deprecated", metadata.isDeprecated());
        
        // 实现数量
        exportData.put("implementationCount", metadata.getImplementations().size());
        
        // 默认实现
        exportData.put("hasDefaultImplementation", false);
        
        // 实现详情（简化实现）
        Map<String, Object> implementations = new HashMap<>();
        exportData.put("implementations", implementations);
        
        return exportData;
    }
    
    /**
     * 将实现元数据转换为导出格式
     */
    private Map<String, Object> convertImplToExportFormat(ExtImplMetadata implMetadata) {
        Map<String, Object> implData = new HashMap<>();
        
        // 实现基本信息
        implData.put("className", implMetadata.getClassName());
        implData.put("name", implMetadata.getName());
        implData.put("description", implMetadata.getDescription());
        implData.put("author", implMetadata.getAuthor());
        implData.put("version", implMetadata.getVersion());
        implData.put("since", implMetadata.getSince());
        implData.put("deprecated", implMetadata.isDeprecated());
        
        // 路由信息
        implData.put("tenantCode", implMetadata.getTenantCode());
        implData.put("bizCode", implMetadata.getBizCode());
        implData.put("useCase", implMetadata.getUseCase());
        implData.put("scenario", implMetadata.getScenario());
        
        // 元数据属性
        implData.put("priority", implMetadata.getPriority());
        implData.put("isDefault", implMetadata.isDefaultImpl());
        implData.put("recommended", implMetadata.isRecommended());
        implData.put("deprecatedSince", implMetadata.getDeprecatedSince());
        implData.put("expiredSince", implMetadata.getExpiredSince());
        
        // 依赖信息
        implData.put("dependsOn", implMetadata.getDependsOn());
        
        // 配置属性
        implData.put("configProperties", implMetadata.getConfigProperties());
        
        // 路由条件
        implData.put("routingConditions", implMetadata.getRoutingConditions());
        
        return implData;
    }
    
    /**
     * 导出为简洁格式（只包含关键信息）
     */
    public String exportAsSummaryJson() {
        Map<String, Object> summary = new HashMap<>();
        
        // 临时使用空Map替代遍历操作
        
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (IOException e) {
            log.severe("Failed to export summary to JSON: " + e.getMessage());
            return "{}";
        }
    }
}