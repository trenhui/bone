package com.bone.smartmeta.engine.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

/**
 * 转换引擎
 * 负责数据格式转换、字段映射、数据提取和合并等功能
 */
public class TransformationEngine {

    private static final Logger logger = LoggerFactory.getLogger(TransformationEngine.class);
    
    // 使用LinkedHashMap实现LRU缓存
    private final Map<String, Map<String, Object>> transformationCache;
    
    // 缓存配置
    private final int cacheSizeLimit;
    private final boolean cacheEnabled;
    
    // 转换配置
    private final boolean normalizeEnabled;
    
    /**
     * 默认构造函数
     */
    public TransformationEngine() {
        this(1000, true, true);
    }
    
    /**
     * 自定义配置的构造函数
     * @param cacheSizeLimit 缓存大小限制
     * @param cacheEnabled 是否启用缓存
     * @param normalizeEnabled 是否启用数据规范化
     */
    public TransformationEngine(int cacheSizeLimit, boolean cacheEnabled, boolean normalizeEnabled) {
        // 参数验证
        if (cacheSizeLimit <= 0) {
            throw new IllegalArgumentException("Cache size limit must be positive");
        }
        
        this.cacheSizeLimit = cacheSizeLimit;
        this.cacheEnabled = cacheEnabled;
        this.normalizeEnabled = normalizeEnabled;
        
        // 使用LinkedHashMap实现LRU缓存
        if (cacheEnabled) {
            this.transformationCache = new LinkedHashMap<String, Map<String, Object>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, Object>> eldest) {
                    return size() > cacheSizeLimit;
                }
            };
            logger.info("TransformationEngine initialized with LRU caching capability, size limit: {}", cacheSizeLimit);
        } else {
            this.transformationCache = new HashMap<>();
            logger.info("TransformationEngine initialized without caching");
        }
    }

    /**
     * 根据实体名称转换数据格式
     * @param entityName 实体名称
     * @param sourceData 源数据
     * @return 转换后的数据
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws TransformationException 当转换失败时抛出
     */
    public Map<String, Object> transformData(String entityName, Map<String, Object> sourceData) {
        // 参数验证
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        Objects.requireNonNull(sourceData, "Source data cannot be null");
        
        // 生成缓存键
        String cacheKey = generateCacheKey(entityName, sourceData);
        
        // 尝试从缓存获取结果
        if (cacheEnabled && transformationCache.containsKey(cacheKey)) {
            logger.debug("Cache hit for transformation: {}", cacheKey);
            return new HashMap<>(transformationCache.get(cacheKey));
        }
        
        try {
            // 执行数据转换
            Map<String, Object> transformedData = new HashMap<>(sourceData);
            
            // 执行数据规范化（如果启用）
            if (normalizeEnabled) {
                transformedData = normalizeData(transformedData);
            }
            
            // 执行实体特定的数据转换规则
            transformedData = applyEntitySpecificRules(entityName, transformedData);
            
            // 缓存结果（如果启用）
            if (cacheEnabled) {
                cacheResult(cacheKey, transformedData);
            }
            
            logger.debug("Successfully transformed data for entity: {}", entityName);
            return transformedData;
        } catch (TransformationException e) {
            // 直接重新抛出TransformationException
            throw e;
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
     * @throws TransformationException 当转换失败时抛出
     */
    public List<Map<String, Object>> transformDataBatch(String entityName, List<Map<String, Object>> sourceDataList) {
        // 参数验证
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        Objects.requireNonNull(sourceDataList, "Source data list cannot be null");
        
        logger.debug("Starting batch transformation for {} items of entity: {}", sourceDataList.size(), entityName);
        
        try {
            // 根据数据量大小决定是否使用并行流
            if (sourceDataList.size() > 10) {
                // 大数据量使用并行流
                List<Map<String, Object>> result = sourceDataList.parallelStream()
                        .filter(Objects::nonNull) // 过滤null值
                        .map(data -> transformData(entityName, data))
                        .collect(Collectors.toList());
                
                logger.debug("Completed parallel batch transformation for {} items of entity: {}", result.size(), entityName);
                return result;
            } else {
                // 小数据量使用顺序流，避免并行开销
                List<Map<String, Object>> result = sourceDataList.stream()
                        .filter(Objects::nonNull) // 过滤null值
                        .map(data -> transformData(entityName, data))
                        .collect(Collectors.toList());
                
                logger.debug("Completed sequential batch transformation for {} items of entity: {}", result.size(), entityName);
                return result;
            }
        } catch (TransformationException e) {
            // 直接重新抛出TransformationException
            throw e;
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
     * @throws TransformationException 当提取失败时抛出
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
            // 移除重复字段名以避免冲突
            List<String> uniqueFieldNames = fieldNames.stream()
                    .distinct()
                    .collect(Collectors.toList());
            
            // 使用流进行字段提取，更简洁高效
            Map<String, Object> result = uniqueFieldNames.stream()
                    .filter(sourceData::containsKey)
                    .collect(Collectors.toMap(
                            Function.identity(),
                            sourceData::get,
                            (v1, v2) -> v1,  // 处理重复键的情况
                            LinkedHashMap::new  // 保持原始字段顺序
                    ));
            
            // 记录缺失的字段
            if (result.size() < uniqueFieldNames.size()) {
                List<String> missingFields = uniqueFieldNames.stream()
                        .filter(field -> !result.containsKey(field))
                        .collect(Collectors.toList());
                logger.debug("Extraction missing {} fields: {}", missingFields.size(), missingFields);
            }
            
            logger.debug("Successfully extracted {} fields", result.size());
            return result;
        } catch (TransformationException e) {
            // 直接重新抛出TransformationException
            throw e;
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
     * @throws TransformationException 当合并失败时抛出
     */
    public Map<String, Object> mergeData(List<Map<String, Object>> dataSources) {
        // 参数验证
        Objects.requireNonNull(dataSources, "Data sources cannot be null");
        
        // 过滤并计算实际数据源数量
        List<Map<String, Object>> validDataSources = dataSources.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        logger.debug("Merging {} valid data sources out of {}", validDataSources.size(), dataSources.size());
        
        try {
            if (validDataSources.isEmpty()) {
                logger.warn("No valid data sources provided for merging");
                return new HashMap<>();
            }
            
            // 使用LinkedHashMap保持插入顺序
            Map<String, Object> mergedData = new LinkedHashMap<>();
            
            // 记录键冲突情况
            Map<String, Integer> keyConflictCount = new HashMap<>();
            
            // 按顺序合并数据，后出现的数据会覆盖前面的数据
            for (Map<String, Object> source : validDataSources) {
                for (Map.Entry<String, Object> entry : source.entrySet()) {
                    String key = entry.getKey();
                    if (mergedData.containsKey(key)) {
                        keyConflictCount.put(key, keyConflictCount.getOrDefault(key, 0) + 1);
                    }
                    mergedData.put(key, entry.getValue());
                }
            }
            
            // 记录冲突情况
            if (!keyConflictCount.isEmpty()) {
                logger.debug("Found {} key conflicts during merge: {}", keyConflictCount.size(), keyConflictCount.keySet());
            }
            
            logger.debug("Successfully merged data with {} entries", mergedData.size());
            return mergedData;
        } catch (TransformationException e) {
            // 直接重新抛出TransformationException
            throw e;
        } catch (Exception e) {
            logger.error("Error merging data sources: {}", e.getMessage(), e);
            throw new TransformationException("Failed to merge data sources", e);
        }
    }
    
    /**
     * 应用实体特定的数据转换规则
     * @param entityName 实体名称
     * @param data 要处理的数据
     * @return 应用规则后的数据
     */
    private Map<String, Object> applyEntitySpecificRules(String entityName, Map<String, Object> data) {
        // 这里可以根据不同的实体名称应用不同的转换规则
        // 当前实现一个基础版本，可以根据实际需求扩展
        Map<String, Object> result = new HashMap<>(data);
        
        // 示例：根据实体类型应用不同的规则
        switch (entityName.toLowerCase()) {
            case "supplier":
                // 供应商特定规则
                applySupplierRules(result);
                break;
            case "purchaseorder":
                // 采购订单特定规则
                applyPurchaseOrderRules(result);
                break;
            default:
                // 默认规则
                applyDefaultRules(result);
                break;
        }
        
        return result;
    }
    
    /**
     * 应用供应商特定的转换规则
     */
    private void applySupplierRules(Map<String, Object> data) {
        // 供应商相关字段的特定处理
        // 例如：标准化联系方式、格式化税号等
        if (data.containsKey("contactPhone")) {
            Object phone = data.get("contactPhone");
            if (phone instanceof String) {
                // 简化版：移除非数字字符（实际应用中可能需要更复杂的格式化逻辑）
                String normalizedPhone = ((String) phone).replaceAll("\\D", "");
                data.put("contactPhone", normalizedPhone);
            }
        }
    }
    
    /**
     * 应用采购订单特定的转换规则
     */
    private void applyPurchaseOrderRules(Map<String, Object> data) {
        // 采购订单相关字段的特定处理
        // 例如：确保金额格式正确、日期一致性检查等
        if (data.containsKey("totalAmount")) {
            Object amount = data.get("totalAmount");
            if (amount instanceof String) {
                try {
                    // 尝试转换为数字
                    Double numericAmount = Double.parseDouble((String) amount);
                    data.put("totalAmount", numericAmount);
                } catch (NumberFormatException e) {
                    logger.warn("Failed to parse totalAmount as number: {}", amount);
                }
            }
        }
    }
    
    /**
     * 应用默认转换规则
     */
    private void applyDefaultRules(Map<String, Object> data) {
        // 适用于所有实体的默认规则
        // 这里可以添加通用的转换逻辑
    }
    
    /**
     * 规范化数据，执行基本的数据清洗
     */
    private Map<String, Object> normalizeData(Map<String, Object> data) {
        Map<String, Object> normalized = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 处理null值
            if (value == null) {
                // 可以选择跳过或保留，根据需求调整
                continue;
            }
            
            // 对字符串进行trim处理
            if (value instanceof String) {
                String strValue = ((String) value).trim();
                // 跳过空字符串
                if (!strValue.isEmpty()) {
                    normalized.put(key, strValue);
                }
            } 
            // 处理集合类型
            else if (value instanceof Collection) {
                Collection<?> collection = (Collection<?>) value;
                if (!collection.isEmpty()) {
                    // 移除集合中的null元素
                    List<?> filteredCollection = collection.stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    
                    if (!filteredCollection.isEmpty()) {
                        normalized.put(key, filteredCollection);
                    }
                }
            }
            // 处理Map类型
            else if (value instanceof Map) {
                Map<?, ?> mapValue = (Map<?, ?>) value;
                if (!mapValue.isEmpty()) {
                    normalized.put(key, new HashMap<>(mapValue));
                }
            }
            // 其他类型直接保留
            else {
                normalized.put(key, value);
            }
        }
        
        return normalized;
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String entityName, Map<String, Object> data) {
        // 增强的缓存键生成策略
        // 使用实体名称和数据的有序字段生成更可靠的缓存键
        StringBuilder sb = new StringBuilder(entityName);
        sb.append(":");
        
        // 获取键并排序，确保生成一致的缓存键
        List<String> sortedKeys = new ArrayList<>(data.keySet());
        Collections.sort(sortedKeys);
        
        for (String key : sortedKeys) {
            Object value = data.get(key);
            sb.append(key).append("=");
            
            // 为不同类型的值生成不同的表示
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String) {
                // 对字符串进行hash，避免缓存键过长
                sb.append(value.hashCode());
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                // 对于复杂对象，使用其hashCode
                sb.append(value.hashCode());
            }
            sb.append(",");
        }
        
        // 对最终结果进行hash，进一步缩短缓存键
        return String.valueOf(sb.toString().hashCode());
    }
    
    /**
     * 缓存转换结果
     */
    private void cacheResult(String key, Map<String, Object> result) {
        // 检查缓存是否启用
        if (!cacheEnabled) {
            return;
        }
        
        // 由于现在使用LinkedHashMap实现LRU缓存，不再需要手动管理缓存大小
        // LinkedHashMap会自动根据removeEldestEntry方法删除最老的条目
        transformationCache.put(key, new HashMap<>(result));
        
        // 记录缓存统计信息
        if (transformationCache.size() % 100 == 0) {
            logger.debug("Cache size reached: {}/{}", transformationCache.size(), cacheSizeLimit);
        }
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        if (cacheEnabled) {
            transformationCache.clear();
            logger.info("Transformation cache cleared");
        } else {
            logger.warn("Cache is disabled, cannot clear");
        }
    }
    
    /**
     * 获取当前缓存大小
     */
    public int getCacheSize() {
        return transformationCache.size();
    }
    
    /**
     * 获取缓存配置信息
     */
    public TransformationConfig getConfig() {
        return new TransformationConfig(cacheSizeLimit, cacheEnabled, normalizeEnabled);
    }
    
    /**
     * 转换配置类
     * 用于存储和获取引擎的配置信息
     */
    public static class TransformationConfig {
        private final int cacheSizeLimit;
        private final boolean cacheEnabled;
        private final boolean normalizeEnabled;
        
        public TransformationConfig(int cacheSizeLimit, boolean cacheEnabled, boolean normalizeEnabled) {
            this.cacheSizeLimit = cacheSizeLimit;
            this.cacheEnabled = cacheEnabled;
            this.normalizeEnabled = normalizeEnabled;
        }
        
        public int getCacheSizeLimit() {
            return cacheSizeLimit;
        }
        
        public boolean isCacheEnabled() {
            return cacheEnabled;
        }
        
        public boolean isNormalizeEnabled() {
            return normalizeEnabled;
        }
        
        @Override
        public String toString() {
            return "TransformationConfig{" +
                    "cacheSizeLimit=" + cacheSizeLimit +
                    ", cacheEnabled=" + cacheEnabled +
                    ", normalizeEnabled=" + normalizeEnabled +
                    '}';
        }
    }
    
    /**
     * 转换异常类
     * 提供更详细的错误信息和上下文
     */
    public static class TransformationException extends RuntimeException {
        // 错误代码，用于更好地分类和处理异常
        private final String errorCode;
        // 相关实体名称
        private final String entityName;
        // 错误数据上下文
        private final Map<String, Object> contextData;
        
        public TransformationException(String message) {
            this(message, "TRANSFORMATION_ERROR", null, null);
        }

        public TransformationException(String message, Throwable cause) {
            this(message, "TRANSFORMATION_ERROR", null, cause);
        }
        
        /**
         * 带错误代码和实体名称的构造函数
         */
        public TransformationException(String message, String errorCode, String entityName) {
            this(message, errorCode, entityName, null);
        }
        
        /**
         * 完整构造函数，提供所有错误上下文信息
         */
        public TransformationException(String message, String errorCode, String entityName, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
            this.entityName = entityName;
            this.contextData = new HashMap<>();
        }
        
        /**
         * 添加上下文数据，用于调试和错误处理
         */
        public TransformationException addContextData(String key, Object value) {
            if (key != null && value != null) {
                contextData.put(key, value);
            }
            return this;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
        
        public String getEntityName() {
            return entityName;
        }
        
        public Map<String, Object> getContextData() {
            return new HashMap<>(contextData);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("TransformationException{");
            sb.append("errorCode='").append(errorCode).append("'").append(", ");
            if (entityName != null) {
                sb.append("entityName='").append(entityName).append("'").append(", ");
            }
            sb.append("message='").append(getMessage()).append("'");
            if (!contextData.isEmpty()) {
                sb.append(", contextData=").append(contextData);
            }
            sb.append('}');
            return sb.toString();
        }
    }
}