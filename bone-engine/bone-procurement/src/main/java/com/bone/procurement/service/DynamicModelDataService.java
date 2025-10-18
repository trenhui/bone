package com.bone.procurement.service;

import com.bone.procurement.config.DynamicModelConfig;
import com.bone.procurement.exception.BusinessException;
import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 动态模型数据服务
 * 基于元数据引擎的数据操作服务，支持增删改查
 * 遵循Salesforce、Workday等最佳实践，通过元数据驱动的数据处理
 */
@Service
public class DynamicModelDataService {

    /**
     * 查询操作类型枚举
     */
    public enum QueryOperator {
        EQ, // 等于
        NEQ, // 不等于
        GT, // 大于
        LT, // 小于
        GTE, // 大于等于
        LTE, // 小于等于
        LIKE, // 模糊匹配
        IN, // 在列表中
        NOT_IN, // 不在列表中
        IS_NULL, // 为空
        IS_NOT_NULL // 不为空
    }

    private static final Logger log = LoggerFactory.getLogger(DynamicModelDataService.class);

    @Autowired
    private DynamicModelManager dynamicModelManager;

    @Autowired
    private DynamicModelConfig dynamicModelConfig;
    
    @Autowired
    private MetadataEngine metadataEngine;

    // 临时数据存储（在生产环境中应替换为实际的持久化存储）
    private final Map<String, Map<String, Map<String, Object>>> modelDataStore = new ConcurrentHashMap<>();
    
    // 数据版本计数器，用于乐观锁
    private final Map<String, AtomicLong> versionCounters = new ConcurrentHashMap<>();
    
    // 历史数据存储（简化实现）
    private final Map<String, Map<String, List<Map<String, Object>>>> historyStore = new ConcurrentHashMap<>();

    /**
     * 创建动态模型数据
     */
    public Map<String, Object> createData(String modelName, Map<String, Object> data) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(data, "数据不能为空");
        
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 验证数据
        validateData(modelName, data, entityMetadata);
        
        // 生成唯一ID
        String id = UUID.randomUUID().toString();
        Map<String, Object> processedData = new HashMap<>(data);
        processedData.put("id", id);
        
        // 设置创建时间戳
        long timestamp = System.currentTimeMillis();
        processedData.put("createdAt", timestamp);
        processedData.put("updatedAt", timestamp);
        
        // 设置版本号（乐观锁）
        processedData.put("version", 1L);

        // 初始化模型数据存储（如果不存在）
        modelDataStore.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>());
        versionCounters.computeIfAbsent(modelName, k -> new AtomicLong(0));

        // 存储数据
        modelDataStore.get(modelName).put(id, processedData);
        
        // 处理计算字段和虚拟字段
        processedData = metadataEngine.processEntityInstance(modelName, processedData);

        log.info("创建动态模型数据成功: {}, ID: {}", modelName, id);
        return new HashMap<>(processedData); // 返回副本，避免外部修改
    }

    /**
     * 根据ID获取动态模型数据
     */
    public Map<String, Object> getDataById(String modelName, String id) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "ID不能为空");
        
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 获取数据
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null || !modelData.containsKey(id)) {
            return null;
        }
        
        // 获取数据并处理计算字段和虚拟字段
        Map<String, Object> data = modelData.get(id);
        return metadataEngine.processEntityInstance(modelName, new HashMap<>(data));
    }

    /**
     * 获取动态模型的所有数据（带分页）
     */
    public List<Map<String, Object>> getAllData(String modelName, int page, int pageSize) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.isTrue(page >= 1, "页码必须大于等于1");
        Assert.isTrue(pageSize >= 1 && pageSize <= 1000, "每页大小必须在1-1000之间");
        
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 获取数据
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null) {
            return Collections.emptyList();
        }
        
        // 转换为列表并应用分页
        List<Map<String, Object>> allData = new ArrayList<>(modelData.values());
        
        // 计算分页范围
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allData.size());
        
        if (startIndex >= allData.size()) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> paginatedData = allData.subList(startIndex, endIndex);
        
        // 批量处理计算字段和虚拟字段
        return metadataEngine.processEntityInstancesBatch(modelName, 
                paginatedData.stream().map(HashMap::new).collect(Collectors.toList()));
    }
    
    /**
     * 获取动态模型的所有数据（不分页）
     */
    public List<Map<String, Object>> getAllData(String modelName) {
        return getAllData(modelName, 1, Integer.MAX_VALUE);
    }

    /**
     * 更新动态模型数据（带乐观锁）
     */
    public Map<String, Object> updateData(String modelName, String id, Map<String, Object> data, Long expectedVersion) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "ID不能为空");
        Assert.notNull(data, "数据不能为空");
        
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 检查数据是否存在
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null || !modelData.containsKey(id)) {
            throw new BusinessException("数据不存在: " + id);
        }

        // 获取现有数据并验证版本
        Map<String, Object> existingData = new HashMap<>(modelData.get(id));
        
        // 乐观锁验证
        Long currentVersion = (Long) existingData.getOrDefault("version", 1L);
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new BusinessException("数据已被其他用户修改，请刷新后重试。当前版本: " + currentVersion);
        }
        
        // 保存历史版本
        saveHistoryVersion(modelName, id, existingData);
        
        // 验证更新的数据
        Map<String, Object> updateData = new HashMap<>(data);
        validateData(modelName, updateData, entityMetadata);
        
        // 合并更新的数据（排除不可修改字段）
        updateData.forEach((key, value) -> {
            if (!"id".equals(key) && !"createdAt".equals(key) && !"version".equals(key)) {
                existingData.put(key, value);
            }
        });
        
        // 更新时间戳和版本号
        existingData.put("updatedAt", System.currentTimeMillis());
        existingData.put("version", currentVersion + 1);

        // 更新数据
        modelData.put(id, existingData);
        
        // 处理计算字段和虚拟字段
        Map<String, Object> processedData = metadataEngine.processEntityInstance(modelName, new HashMap<>(existingData));

        log.info("更新动态模型数据成功: {}, ID: {}, 版本: {}", modelName, id, existingData.get("version"));
        return processedData;
    }
    
    /**
     * 更新动态模型数据（不带乐观锁）
     */
    public Map<String, Object> updateData(String modelName, String id, Map<String, Object> data) {
        return updateData(modelName, id, data, null);
    }

    /**
     * 删除动态模型数据（带软删除功能）
     */
    public boolean deleteData(String modelName, String id) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "ID不能为空");
        
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 检查数据是否存在
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null || !modelData.containsKey(id)) {
            return false;
        }
        
        // 保存历史版本用于恢复
        Map<String, Object> dataToDelete = new HashMap<>(modelData.get(id));
        saveHistoryVersion(modelName, id, dataToDelete);
        
        // 软删除实现：标记删除
        dataToDelete.put("deleted", true);
        dataToDelete.put("deletedAt", System.currentTimeMillis());
        dataToDelete.put("version", ((Long) dataToDelete.getOrDefault("version", 1L)) + 1);
        modelData.put(id, dataToDelete);
        
        log.info("删除动态模型数据成功: {}, ID: {}", modelName, id);
        return true;
    }
    
    /**
     * 硬删除动态模型数据（直接删除）
     */
    public boolean hardDeleteData(String modelName, String id) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "ID不能为空");
        
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 检查数据是否存在
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null) {
            return false;
        }

        // 保存历史版本
        if (modelData.containsKey(id)) {
            saveHistoryVersion(modelName, id, new HashMap<>(modelData.get(id)));
        }
        
        // 直接删除数据
        boolean removed = modelData.remove(id) != null;

        if (removed) {
            log.info("硬删除动态模型数据成功: {}, ID: {}", modelName, id);
        }

        return removed;
    }

    /**
     * 查询动态模型数据（支持复杂条件和分页）
     */
    public List<Map<String, Object>> queryData(String modelName, Map<String, Object> queryConditions, 
                                             int page, int pageSize, Map<String, Boolean> sortOrders) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.isTrue(page >= 1, "页码必须大于等于1");
        Assert.isTrue(pageSize >= 1 && pageSize <= 1000, "每页大小必须在1-1000之间");
        
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 获取数据
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null) {
            return Collections.emptyList();
        }

        // 过滤数据（排除已软删除的数据）
        List<Map<String, Object>> filteredData = modelData.values().stream()
                .filter(data -> !Boolean.TRUE.equals(data.get("deleted")))
                .filter(data -> matchesConditions(data, queryConditions))
                .collect(Collectors.toList());
        
        // 应用排序
        if (sortOrders != null && !sortOrders.isEmpty()) {
            filteredData.sort((data1, data2) -> {
                for (Map.Entry<String, Boolean> sortOrder : sortOrders.entrySet()) {
                    String field = sortOrder.getKey();
                    boolean ascending = sortOrder.getValue();
                    
                    Comparable<?> value1 = (Comparable<?>) data1.getOrDefault(field, null);
                    Comparable<?> value2 = (Comparable<?>) data2.getOrDefault(field, null);
                    
                    if (value1 == null && value2 == null) {
                        continue;
                    } else if (value1 == null) {
                        return ascending ? -1 : 1;
                    } else if (value2 == null) {
                        return ascending ? 1 : -1;
                    }
                    
                    int compareResult = value1.compareTo(value2);
                    if (compareResult != 0) {
                        return ascending ? compareResult : -compareResult;
                    }
                }
                return 0;
            });
        }
        
        // 应用分页
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filteredData.size());
        
        if (startIndex >= filteredData.size()) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> paginatedData = filteredData.subList(startIndex, endIndex);
        
        // 批量处理计算字段和虚拟字段
        return metadataEngine.processEntityInstancesBatch(modelName, 
                paginatedData.stream().map(HashMap::new).collect(Collectors.toList()));
    }
    
    /**
     * 查询动态模型数据（简化版本）
     */
    public List<Map<String, Object>> queryData(String modelName, Map<String, Object> queryConditions) {
        return queryData(modelName, queryConditions, 1, Integer.MAX_VALUE, null);
    }

    /**
     * 检查数据是否匹配查询条件（支持复杂条件）
     */
    private boolean matchesConditions(Map<String, Object> data, Map<String, Object> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        
        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            String fieldName = condition.getKey();
            Object expectedValue = condition.getValue();
            
            // 检查是否是带操作符的条件格式 {"fieldName.op": value}
            int dotIndex = fieldName.indexOf('.');
            if (dotIndex > 0) {
                String opName = fieldName.substring(dotIndex + 1).toUpperCase();
                fieldName = fieldName.substring(0, dotIndex);
                
                try {
                    QueryOperator operator = QueryOperator.valueOf(opName);
                    if (!matchesConditionWithOperator(data, fieldName, expectedValue, operator)) {
                        return false;
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("未知的查询操作符: {}", opName);
                    return false;
                }
            } else {
                // 默认等于操作符
                if (!matchesConditionWithOperator(data, fieldName, expectedValue, QueryOperator.EQ)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 使用指定操作符检查条件匹配
     */
    private boolean matchesConditionWithOperator(Map<String, Object> data, String fieldName, 
                                               Object expectedValue, QueryOperator operator) {
        // 检查字段是否存在
        boolean fieldExists = data.containsKey(fieldName);
        Object actualValue = data.get(fieldName);
        
        switch (operator) {
            case EQ:
                return fieldExists && Objects.equals(actualValue, expectedValue);
            case NEQ:
                return !fieldExists || !Objects.equals(actualValue, expectedValue);
            case GT:
                return compareValues(actualValue, expectedValue) > 0;
            case LT:
                return compareValues(actualValue, expectedValue) < 0;
            case GTE:
                return compareValues(actualValue, expectedValue) >= 0;
            case LTE:
                return compareValues(actualValue, expectedValue) <= 0;
            case LIKE:
                if (actualValue instanceof String && expectedValue instanceof String) {
                    return ((String) actualValue).contains((String) expectedValue);
                }
                return false;
            case IN:
                if (expectedValue instanceof Collection) {
                    return ((Collection<?>) expectedValue).contains(actualValue);
                }
                return false;
            case NOT_IN:
                if (expectedValue instanceof Collection) {
                    return !((Collection<?>) expectedValue).contains(actualValue);
                }
                return true;
            case IS_NULL:
                return actualValue == null;
            case IS_NOT_NULL:
                return actualValue != null;
            default:
                return false;
        }
    }
    
    /**
     * 比较两个值的大小
     */
    private int compareValues(Object value1, Object value2) {
        if (value1 == null && value2 == null) {
            return 0;
        } else if (value1 == null) {
            return -1;
        } else if (value2 == null) {
            return 1;
        }
        
        if (value1 instanceof Comparable && value1.getClass().equals(value2.getClass())) {
            try {
                return ((Comparable) value1).compareTo(value2);
            } catch (ClassCastException e) {
                log.warn("无法比较的值类型: {}, {}", value1.getClass(), value2.getClass());
            }
        }
        
        return 0;
    }
    
    /**
     * 验证数据是否符合模型定义
     */
    private void validateData(String modelName, Map<String, Object> data, EntityMetadata entityMetadata) {
        // 这里可以利用元数据引擎的验证功能
        // 简单实现：确保必填字段存在
        // TODO: 实现更复杂的验证逻辑
        log.debug("验证动态模型数据: {}", modelName);
    }
    
    /**
     * 保存数据的历史版本
     */
    private void saveHistoryVersion(String modelName, String id, Map<String, Object> data) {
        historyStore.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>())
                   .computeIfAbsent(id, k -> new ArrayList<>())
                   .add(new HashMap<>(data));
        
        log.debug("保存数据历史版本: {}, ID: {}", modelName, id);
    }
    
    /**
     * 获取数据的历史版本
     */
    public List<Map<String, Object>> getHistoryVersions(String modelName, String id, int limit) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "ID不能为空");
        Assert.isTrue(limit > 0, "限制数量必须大于0");
        
        Map<String, List<Map<String, Object>>> modelHistory = historyStore.get(modelName);
        if (modelHistory == null) {
            return Collections.emptyList();
        }
        
        List<Map<String, Object>> history = modelHistory.get(id);
        if (history == null) {
            return Collections.emptyList();
        }
        
        // 返回最近的limit条历史记录
        int startIndex = Math.max(0, history.size() - limit);
        return history.subList(startIndex, history.size());
    }
    
    /**
     * 恢复数据到指定版本
     */
    public Map<String, Object> restoreVersion(String modelName, String id, int versionIndex) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "ID不能为空");
        
        Map<String, List<Map<String, Object>>> modelHistory = historyStore.get(modelName);
        if (modelHistory == null) {
            throw new BusinessException("没有找到历史记录");
        }
        
        List<Map<String, Object>> history = modelHistory.get(id);
        if (history == null || versionIndex < 0 || versionIndex >= history.size()) {
            throw new BusinessException("指定的历史版本不存在");
        }
        
        // 获取历史版本数据
        Map<String, Object> historicalData = new HashMap<>(history.get(versionIndex));
        
        // 保存当前版本到历史
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData != null && modelData.containsKey(id)) {
            saveHistoryVersion(modelName, id, new HashMap<>(modelData.get(id)));
        }
        
        // 恢复数据并更新版本和时间戳
        historicalData.remove("deleted");
        historicalData.remove("deletedAt");
        historicalData.put("updatedAt", System.currentTimeMillis());
        historicalData.put("version", ((Long) historicalData.getOrDefault("version", 1L)) + 1);
        
        // 存储恢复后的数据
        modelDataStore.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>()).put(id, historicalData);
        
        log.info("恢复数据版本成功: {}, ID: {}, 版本索引: {}", modelName, id, versionIndex);
        return historicalData;
    }
    
    /**
     * 批量创建数据（优化性能）
     */
    public List<Map<String, Object>> batchCreateData(String modelName, List<Map<String, Object>> dataList) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(dataList, "数据列表不能为空");
        
        log.info("批量创建动态模型数据: {}, 数量: {}", modelName, dataList.size());
        
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("模型不存在: " + modelName);
        }
        
        // 初始化存储
        modelDataStore.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>());
        versionCounters.computeIfAbsent(modelName, k -> new AtomicLong(0));
        
        // 并行处理批量创建
        List<Map<String, Object>> createdData = dataList.parallelStream()
                .map(data -> {
                    try {
                        // 创建单条数据
                        return createData(modelName, data);
                    } catch (Exception e) {
                        log.warn("创建数据失败: {}, 数据: {}", modelName, data, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        log.info("批量创建完成: {}, 创建数量: {}", modelName, createdData.size());
        return createdData;
    }
    
    /**
     * 批量删除数据（优化性能）
     */
    public int batchDeleteData(String modelName, List<String> ids) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(ids, "ID列表不能为空");
        
        log.info("批量删除动态模型数据: {}, 数量: {}", modelName, ids.size());
        
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("模型不存在: " + modelName);
        }
        
        // 并行处理批量删除
        AtomicLong deletedCount = new AtomicLong(0);
        ids.parallelStream().forEach(id -> {
            try {
                // 删除单条数据
                if (deleteData(modelName, id)) {
                    deletedCount.incrementAndGet();
                }
            } catch (Exception e) {
                log.warn("删除数据失败: {}, ID: {}", modelName, id, e);
            }
        });
        
        log.info("批量删除完成: {}, 删除数量: {}", modelName, deletedCount.get());
        return deletedCount.intValue();
    }

    /**
     * 清空指定模型的数据（先备份再清空）
     */
    public void clearModelData(String modelName) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        
        if (modelDataStore.containsKey(modelName)) {
            // 备份数据到历史
            Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
            modelData.forEach((id, data) -> {
                saveHistoryVersion(modelName, id, new HashMap<>(data));
            });
            
            // 清空数据
            modelData.clear();
            log.info("已清空模型数据: {}", modelName);
        }
    }

    /**
     * 获取模型数据量
     */
    public int getDataCount(String modelName) {
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        return modelData != null ? modelData.size() : 0;
    }
}