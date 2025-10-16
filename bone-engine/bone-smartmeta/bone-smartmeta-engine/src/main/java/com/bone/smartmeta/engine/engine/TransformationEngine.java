package com.bone.smartmeta.engine.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 转换引擎
 * 负责数据格式转换、字段映射、数据提取和合并等功能
 */
public class TransformationEngine {

    private static final Logger logger = LoggerFactory.getLogger(TransformationEngine.class);
    
    // 用于缓存常用转换结果的简单缓存机制
    private final Map<String, Map<String, Object>> transformationCache;
    
    // 缓存大小限制
    private static final int CACHE_SIZE_LIMIT = 1000;
    
    /**
     * 构造函数
     */
    public TransformationEngine() {
        // 使用ConcurrentHashMap确保线程安全
        this.transformationCache = new ConcurrentHashMap<>();
        logger.info("TransformationEngine initialized with caching capability");
    }

    /**
     * 根据实体名称转换数据格式
     * @param entityName 实体名称
     * @param sourceData 源数据
     * @return 转换后的数据
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    public Map<String, Object> transformData(String entityName, Map<String, Object> sourceData) {
        // 参数验证
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        Objects.requireNonNull(sourceData, "Source data cannot be null");
        
        // 生成缓存键
        String cacheKey = generateCacheKey(entityName, sourceData);
        
        // 尝试从缓存获取结果
        if (transformationCache.containsKey(cacheKey)) {
            logger.debug("Cache hit for transformation: {}", cacheKey);
            return new HashMap<>(transformationCache.get(cacheKey));
        }
        
        try {
            // 执行数据转换（这里实现了基本的转换逻辑）
            Map<String, Object> transformedData = new HashMap<>(sourceData);
            
            // 执行基本的数据清洗和规范化
            transformedData = normalizeData(transformedData);
            
            // 缓存结果
            cacheResult(cacheKey, transformedData);
            
            logger.debug("Successfully transformed data for entity: {}", entityName);
            return transformedData;
        } catch (Exception e) {
            logger.error("Error transforming data for entity {}: {}", entityName, e.getMessage(), e);
            throw new TransformationException("Failed to transform data for entity: " + entityName, e);
        }
    }

    /**
     * 批量转换数据
     * @param entityName 实体名称
     * @param sourceDataList 源数据列表
     * @return 转换后的数据列表
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    public List<Map<String, Object>> transformDataBatch(String entityName, List<Map<String, Object>> sourceDataList) {
        // 参数验证
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        Objects.requireNonNull(sourceDataList, "Source data list cannot be null");
        
        logger.debug("Starting batch transformation for {} items of entity: {}", sourceDataList.size(), entityName);
        
        try {
            // 使用并行流提高批量处理性能
            List<Map<String, Object>> result = sourceDataList.parallelStream()
                    .map(data -> transformData(entityName, data))
                    .collect(Collectors.toList());
            
            logger.debug("Completed batch transformation for {} items of entity: {}", result.size(), entityName);
            return result;
        } catch (Exception e) {
            logger.error("Error in batch transformation for entity {}: {}", entityName, e.getMessage(), e);
            throw new TransformationException("Failed to perform batch transformation for entity: " + entityName, e);
        }
    }

    /**
     * 从实体数据中提取指定字段
     * @param sourceData 源数据
     * @param fieldNames 要提取的字段名称列表
     * @return 提取后的字段数据
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    public Map<String, Object> extractFields(Map<String, Object> sourceData, List<String> fieldNames) {
        // 参数验证
        Objects.requireNonNull(sourceData, "Source data cannot be null");
        Objects.requireNonNull(fieldNames, "Field names cannot be null");
        
        if (fieldNames.isEmpty()) {
            logger.warn("Empty field names list provided for extraction");
            return new HashMap<>();
        }
        
        logger.debug("Extracting {} fields from data", fieldNames.size());
        
        try {
            // 使用流进行字段提取，更简洁高效
            Map<String, Object> result = fieldNames.stream()
                    .filter(sourceData::containsKey)
                    .collect(Collectors.toMap(
                            Function.identity(),
                            sourceData::get,
                            (v1, v2) -> v1,  // 处理重复键的情况
                            HashMap::new
                    ));
            
            logger.debug("Successfully extracted {} fields", result.size());
            return result;
        } catch (Exception e) {
            logger.error("Error extracting fields: {}", e.getMessage(), e);
            throw new TransformationException("Failed to extract fields from source data", e);
        }
    }

    /**
     * 合并多个数据源
     * @param dataSources 数据源列表
     * @return 合并后的数据
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    public Map<String, Object> mergeData(List<Map<String, Object>> dataSources) {
        // 参数验证
        Objects.requireNonNull(dataSources, "Data sources cannot be null");
        
        logger.debug("Merging {} data sources", dataSources.size());
        
        try {
            // 使用流进行数据合并，更简洁高效
            Map<String, Object> mergedData = new HashMap<>();
            dataSources.stream()
                    .filter(Objects::nonNull)  // 过滤掉null的数据源
                    .forEach(mergedData::putAll);
            
            logger.debug("Successfully merged data with {} entries", mergedData.size());
            return mergedData;
        } catch (Exception e) {
            logger.error("Error merging data sources: {}", e.getMessage(), e);
            throw new TransformationException("Failed to merge data sources", e);
        }
    }
    
    /**
     * 规范化数据，执行基本的数据清洗
     */
    private Map<String, Object> normalizeData(Map<String, Object> data) {
        Map<String, Object> normalized = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 移除空值
            if (value != null) {
                // 对字符串进行trim处理
                if (value instanceof String) {
                    normalized.put(key, ((String) value).trim());
                } else {
                    normalized.put(key, value);
                }
            }
        }
        
        return normalized;
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String entityName, Map<String, Object> data) {
        // 使用实体名称和数据的hashCode生成简单的缓存键
        // 在生产环境中可能需要更复杂的缓存键生成策略
        return entityName + ":" + data.hashCode();
    }
    
    /**
     * 缓存转换结果
     */
    private void cacheResult(String key, Map<String, Object> result) {
        // 检查缓存大小，防止内存泄漏
        if (transformationCache.size() >= CACHE_SIZE_LIMIT) {
            // 简单的缓存淘汰策略：移除第一个条目
            String firstKey = transformationCache.keySet().iterator().next();
            transformationCache.remove(firstKey);
            logger.debug("Cache size limit reached, removed oldest entry");
        }
        
        transformationCache.put(key, new HashMap<>(result));
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        transformationCache.clear();
        logger.info("Transformation cache cleared");
    }
    
    /**
     * 获取当前缓存大小
     */
    public int getCacheSize() {
        return transformationCache.size();
    }
    
    /**
     * 转换异常类
     */
    public static class TransformationException extends RuntimeException {
        public TransformationException(String message) {
            super(message);
        }

        public TransformationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}