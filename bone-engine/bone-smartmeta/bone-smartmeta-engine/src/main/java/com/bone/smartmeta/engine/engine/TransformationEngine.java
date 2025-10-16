package com.bone.smartmeta.engine.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 转换引擎 - 极简版本
 */
public class TransformationEngine {

    // 移除对MetadataRegistry的依赖

    /**
     * 构造函数
     */
    public TransformationEngine() {
        // 无参数构造函数
    }

    /**
     * 根据元数据转换数据格式 - 极简实现
     */
    public Map<String, Object> transformData(String entityName, Map<String, Object> sourceData) {
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        Objects.requireNonNull(sourceData, "Source data cannot be null");
        return new HashMap<>(sourceData);
    }

    /**
     * 批量转换数据
     */
    public List<Map<String, Object>> transformDataBatch(String entityName, List<Map<String, Object>> sourceDataList) {
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        Objects.requireNonNull(sourceDataList, "Source data list cannot be null");
        
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> data : sourceDataList) {
            result.add(transformData(entityName, data));
        }
        return result;
    }

    /**
     * 从实体数据中提取指定字段
     */
    public Map<String, Object> extractFields(Map<String, Object> sourceData, List<String> fieldNames) {
        Objects.requireNonNull(sourceData, "Source data cannot be null");
        Objects.requireNonNull(fieldNames, "Field names cannot be null");

        Map<String, Object> result = new HashMap<>();
        for (String fieldName : fieldNames) {
            if (sourceData.containsKey(fieldName)) {
                result.put(fieldName, sourceData.get(fieldName));
            }
        }
        return result;
    }

    /**
     * 合并多个数据源
     */
    public Map<String, Object> mergeData(List<Map<String, Object>> dataSources) {
        Objects.requireNonNull(dataSources, "Data sources cannot be null");

        Map<String, Object> mergedData = new HashMap<>();
        for (Map<String, Object> dataSource : dataSources) {
            if (dataSource != null) {
                mergedData.putAll(dataSource);
            }
        }
        return mergedData;
    }
}