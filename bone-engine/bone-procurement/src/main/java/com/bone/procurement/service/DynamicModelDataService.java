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
 */
@Service
public class DynamicModelDataService {

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

    // 存储模型数据
    private final Map<String, Map<String, Map<String, Object>>> modelDataStore = new ConcurrentHashMap<>();
    
    // 版本计数器
    private final Map<String, AtomicLong> versionCounters = new ConcurrentHashMap<>();
    
    // 历史记录存储
    private final Map<String, Map<String, List<Map<String, Object>>>> historyStore = new ConcurrentHashMap<>();

    /**
     * 创建数据
     */
    public Map<String, Object> createData(String modelName, Map<String, Object> data) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(data, "创建数据不能为空");
        
        log.info("创建动态模型数据: {}", modelName);
        
        try {
            // 确保模型数据存储存在
            modelDataStore.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>());
            
            // 生成唯一ID
            String id = UUID.randomUUID().toString();
            
            // 创建新数据
            Map<String, Object> newData = new HashMap<>(data);
            newData.put("id", id);
            newData.put("createdAt", new Date());
            newData.put("updatedAt", new Date());
            newData.put("version", 1L);
            newData.put("deleted", false);
            
            // 存储数据
            modelDataStore.get(modelName).put(id, newData);
            
            log.info("创建动态模型数据成功: {}, ID: {}", modelName, id);
            return new HashMap<>(newData);
        } catch (Exception e) {
            log.error("创建动态模型数据失败: {}", modelName, e);
            throw new BusinessException("创建数据失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取数据
     */
    public Map<String, Object> getDataById(String modelName, String id) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "数据ID不能为空");
        
        log.info("获取动态模型数据: {}, ID: {}", modelName, id);
        
        try {
            // 检查模型是否存在
            if (!modelDataStore.containsKey(modelName)) {
                return null;
            }
            
            // 获取数据
            Map<String, Object> data = modelDataStore.get(modelName).get(id);
            
            // 软删除检查
            if (data != null && Boolean.TRUE.equals(data.get("deleted"))) {
                return null;
            }
            
            return data != null ? new HashMap<>(data) : null;
        } catch (Exception e) {
            log.error("获取动态模型数据失败: {}, ID: {}", modelName, id, e);
            throw new BusinessException("获取数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有数据（分页）
     */
    public List<Map<String, Object>> getAllData(String modelName, int page, int pageSize) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.isTrue(page >= 1, "页码必须大于等于1");
        Assert.isTrue(pageSize >= 1 && pageSize <= 1000, "每页大小必须在1-1000之间");
        
        log.info("获取动态模型所有数据: {}, 页码: {}, 每页大小: {}", modelName, page, pageSize);
        
        try {
            // 检查模型是否存在
            if (!modelDataStore.containsKey(modelName)) {
                return new ArrayList<>();
            }
            
            // 获取并过滤未删除的数据
            List<Map<String, Object>> allData = modelDataStore.get(modelName).values().stream()
                .filter(data -> !Boolean.TRUE.equals(data.get("deleted")))
                .map(data -> new HashMap<>(data))
                .sorted(Comparator.comparing(
                    (Map<String, Object> m) -> {
                        Object createdAt = m.get("createdAt");
                        return createdAt != null ? (Date) createdAt : new Date(0);
                    },
                    Comparator.nullsLast(Date::compareTo)
                ).reversed())
                .collect(Collectors.toList());
            
            // 分页处理
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, allData.size());
            
            if (fromIndex >= allData.size()) {
                return new ArrayList<>();
            }
            
            return allData.subList(fromIndex, toIndex);
        } catch (Exception e) {
            log.error("获取动态模型所有数据失败: {}", modelName, e);
            throw new BusinessException("获取数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有数据
     */
    public List<Map<String, Object>> getAllData(String modelName) {
        return getAllData(modelName, 1, Integer.MAX_VALUE);
    }

    /**
     * 更新数据
     */
    public Map<String, Object> updateData(String modelName, String id, Map<String, Object> data, Long expectedVersion) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "数据ID不能为空");
        Assert.notNull(data, "更新数据不能为空");
        
        log.info("更新动态模型数据: {}, ID: {}", modelName, id);
        
        try {
            // 确保模型数据存储存在
            modelDataStore.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>());
            
            // 获取当前数据
            Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
            Map<String, Object> existingData = modelData.get(id);
            
            if (existingData == null) {
                throw new BusinessException("数据不存在: " + id);
            }
            
            // 版本检查
            if (expectedVersion != null && expectedVersion > 0) {
                Long currentVersion = existingData.containsKey("version") ? 
                    (existingData.get("version") instanceof Long ? (Long) existingData.get("version") : 0) : 0;
                if (!expectedVersion.equals(currentVersion)) {
                    throw new BusinessException("数据版本不匹配，更新失败");
                }
            }
            
            // 保存历史记录
            saveHistory(modelName, id, new HashMap<>(existingData));
            
            // 创建新版本
            long newVersion = getNextVersion(modelName);
            
            // 更新数据
            Map<String, Object> updatedData = new HashMap<>(existingData);
            updatedData.putAll(data);
            updatedData.put("updatedAt", new Date());
            updatedData.put("version", newVersion);
            
            // 存储更新后的数据
            modelData.put(id, updatedData);
            
            log.info("更新动态模型数据成功: {}, ID: {}, 版本: {}", modelName, id, newVersion);
            return new HashMap<>(updatedData);
        } catch (BusinessException e) {
            log.error("业务异常: 更新动态模型数据失败 - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("更新动态模型数据失败: {}, ID: {}", modelName, id, e);
            throw new BusinessException("更新数据失败: " + e.getMessage());
        }
    }

    /**
     * 更新数据（不检查版本）
     */
    public Map<String, Object> updateData(String modelName, String id, Map<String, Object> data) {
        return updateData(modelName, id, data, null);
    }

    /**
     * 更新数据（带上下文）
     */
    public Map<String, Object> updateDataWithContext(String modelName, String id, Map<String, Object> data, Map<String, Object> context) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "数据ID不能为空");
        Assert.notNull(data, "更新数据不能为空");
        
        log.info("带上下文更新动态模型数据: {}, ID: {}", modelName, id);
        
        try {
            // 获取当前版本
            Long expectedVersion = null;
            if (context != null && context.containsKey("expectedVersion")) {
                Object versionObj = context.get("expectedVersion");
                expectedVersion = versionObj instanceof Long ? (Long) versionObj : null;
            }
            
            // 调用基本更新方法
            return updateData(modelName, id, data, expectedVersion);
        } catch (BusinessException e) {
            log.error("业务异常: 带上下文更新动态模型数据失败 - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("带上下文更新动态模型数据失败: {}, ID: {}", modelName, id, e);
            throw new BusinessException("更新数据失败: " + e.getMessage());
        }
    }

    /**
     * 删除数据（软删除）
     */
    public boolean deleteData(String modelName, String id) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "数据ID不能为空");
        
        log.info("软删除动态模型数据: {}, ID: {}", modelName, id);
        
        try {
            if (!modelDataStore.containsKey(modelName)) {
                return false;
            }
            
            Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
            Map<String, Object> existingData = modelData.get(id);
            
            if (existingData == null || Boolean.TRUE.equals(existingData.get("deleted"))) {
                return false;
            }
            
            // 保存历史记录
            saveHistory(modelName, id, new HashMap<>(existingData));
            
            // 执行软删除
            Map<String, Object> deleteData = new HashMap<>(existingData);
            deleteData.put("deleted", true);
            deleteData.put("updatedAt", new Date());
            deleteData.put("version", getNextVersion(modelName));
            
            modelData.put(id, deleteData);
            
            log.info("软删除动态模型数据成功: {}, ID: {}", modelName, id);
            return true;
        } catch (Exception e) {
            log.error("软删除动态模型数据失败: {}, ID: {}", modelName, id, e);
            throw new BusinessException("删除数据失败: " + e.getMessage());
        }
    }

    /**
     * 硬删除数据
     */
    public boolean hardDeleteData(String modelName, String id) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "数据ID不能为空");
        
        log.info("硬删除动态模型数据: {}, ID: {}", modelName, id);
        
        try {
            if (!modelDataStore.containsKey(modelName)) {
                return false;
            }
            
            Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
            Map<String, Object> existingData = modelData.get(id);
            
            if (existingData == null) {
                return false;
            }
            
            // 保存历史记录
            saveHistory(modelName, id, new HashMap<>(existingData));
            
            // 执行硬删除
            boolean removed = modelData.remove(id) != null;
            
            log.info("硬删除动态模型数据成功: {}, ID: {}", modelName, id);
            return removed;
        } catch (Exception e) {
            log.error("硬删除动态模型数据失败: {}, ID: {}", modelName, id, e);
            throw new BusinessException("删除数据失败: " + e.getMessage());
        }
    }

    /**
     * 查询数据
     */
    public List<Map<String, Object>> queryData(String modelName, Map<String, Object> queryConditions,
                                              int page, int pageSize, Map<String, Boolean> sortOrders) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.isTrue(page >= 1, "页码必须大于等于1");
        Assert.isTrue(pageSize >= 1 && pageSize <= 1000, "每页大小必须在1-1000之间");
        
        log.info("查询动态模型数据: {}, 页码: {}, 每页大小: {}", modelName, page, pageSize);
        
        try {
            // 检查模型是否存在
            if (!modelDataStore.containsKey(modelName)) {
                return new ArrayList<>();
            }
            
            // 过滤符合条件的数据
            List<Map<String, Object>> allResults = modelDataStore.get(modelName).values().stream()
                .filter(data -> !Boolean.TRUE.equals(data.get("deleted")))
                .filter(data -> queryConditions == null || queryConditions.isEmpty() || matchesConditions(data, queryConditions))
                .map(data -> new HashMap<>(data))
                .collect(Collectors.toList());
            
            // 创建排序比较器并排序
            Comparator<Map<String, Object>> comparator = createComparator(sortOrders);
            List<Map<String, Object>> sortedResults = allResults.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
            
            // 分页处理
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, sortedResults.size());
            
            if (fromIndex >= sortedResults.size()) {
                return new ArrayList<>();
            }
            
            return sortedResults.subList(fromIndex, toIndex);
        } catch (Exception e) {
            log.error("查询动态模型数据失败: {}", modelName, e);
            throw new BusinessException("查询数据失败: " + e.getMessage());
        }
    }

    /**
     * 查询动态模型数据（简化版本）
     */
    public List<Map<String, Object>> queryData(String modelName, Map<String, Object> queryConditions) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        
        log.info("开始简化查询动态模型数据: {}, 条件: {}", modelName, queryConditions);
        
        try {
            return queryData(modelName, queryConditions, 1, Integer.MAX_VALUE, null);
        } catch (BusinessException e) {
            log.error("业务异常: 简化查询动态模型数据失败 - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("简化查询动态模型数据时发生未预期的错误: {}, 模型: {}", e.getMessage(), modelName, e);
            throw new BusinessException("查询数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据总数
     */
    public int getDataCount(String modelName) {
        Assert.hasText(modelName, "模型名称不能为空");
        
        log.debug("获取动态模型数据总数: {}", modelName);
        
        try {
            if (!modelDataStore.containsKey(modelName)) {
                return 0;
            }
            
            // 统计未删除的数据数量
            return (int) modelDataStore.get(modelName).values().stream()
                .filter(data -> !Boolean.TRUE.equals(data.get("deleted")))
                .count();
        } catch (Exception e) {
            log.error("获取动态模型数据总数失败: {}", modelName, e);
            throw new BusinessException("获取数据数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存数据历史记录
     */
    private void saveHistory(String modelName, String id, Map<String, Object> data) {
        try {
            // 确保历史记录存储存在
            historyStore.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>());
            historyStore.get(modelName).computeIfAbsent(id, k -> new ArrayList<>());
            
            // 克隆数据并添加历史记录标识
            Map<String, Object> historyData = new HashMap<>(data);
            historyData.put("historyAt", new Date());
            historyData.put("historyId", UUID.randomUUID().toString());
            
            // 添加到历史记录
            historyStore.get(modelName).get(id).add(historyData);
            
            log.debug("保存动态模型历史记录: {}, ID: {}", modelName, id);
        } catch (Exception e) {
            // 历史记录保存失败不应影响主要操作
            log.error("保存动态模型历史记录失败: {}, ID: {}", modelName, id, e);
        }
    }
    
    /**
     * 检查数据是否匹配查询条件
     */
    private boolean matchesConditions(Map<String, Object> data, Map<String, Object> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String field = entry.getKey();
            Object conditionValue = entry.getValue();
            Object dataValue = data.get(field);
            
            if (!matchesCondition(dataValue, conditionValue)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查单个条件是否匹配
     */
    private boolean matchesCondition(Object dataValue, Object conditionValue) {
        // 基本的相等匹配
        if (dataValue == null && conditionValue == null) {
            return true;
        }
        if (dataValue == null || conditionValue == null) {
            return false;
        }
        
        // 支持Map形式的高级查询条件
        if (conditionValue instanceof Map) {
            Map<String, Object> conditionMap = (Map<String, Object>) conditionValue;
            for (Map.Entry<String, Object> opEntry : conditionMap.entrySet()) {
                String operator = opEntry.getKey();
                Object value = opEntry.getValue();
                
                if (!applyOperator(dataValue, value, operator)) {
                    return false;
                }
            }
            return true;
        }
        
        // 默认进行相等匹配
        return dataValue.equals(conditionValue);
    }
    
    /**
     * 应用查询操作符
     */
    private boolean applyOperator(Object dataValue, Object conditionValue, String operator) {
        try {
            QueryOperator op = QueryOperator.valueOf(operator.toUpperCase());
            
            switch (op) {
                case EQ:
                    return dataValue.equals(conditionValue);
                case NEQ:
                    return !dataValue.equals(conditionValue);
                case GT:
                    return compare(dataValue, conditionValue) > 0;
                case LT:
                    return compare(dataValue, conditionValue) < 0;
                case GTE:
                    return compare(dataValue, conditionValue) >= 0;
                case LTE:
                    return compare(dataValue, conditionValue) <= 0;
                case LIKE:
                    return dataValue.toString().contains(conditionValue.toString());
                case IN:
                    return conditionValue instanceof Collection && 
                           ((Collection<?>) conditionValue).contains(dataValue);
                case NOT_IN:
                    return !(conditionValue instanceof Collection && 
                           ((Collection<?>) conditionValue).contains(dataValue));
                case IS_NULL:
                    return dataValue == null;
                case IS_NOT_NULL:
                    return dataValue != null;
                default:
                    return false;
            }
        } catch (Exception e) {
            log.debug("应用查询操作符失败: {}, 数据值: {}, 条件值: {}", operator, dataValue, conditionValue, e);
            return false;
        }
    }
    
    /**
     * 比较两个值
     */
    private int compare(Object value1, Object value2) {
        if (value1 == value2) return 0;
        if (value1 == null) return -1;
        if (value2 == null) return 1;
        
        // 处理相同类型的比较
        if (value1.getClass().equals(value2.getClass())) {
            if (value1 instanceof Comparable) {
                @SuppressWarnings("unchecked")
                Comparable<Object> comparable = (Comparable<Object>) value1;
                return comparable.compareTo(value2);
            }
        }
        
        // 数值类型比较
        if (value1 instanceof Number && value2 instanceof Number) {
            double d1 = ((Number) value1).doubleValue();
            double d2 = ((Number) value2).doubleValue();
            return Double.compare(d1, d2);
        }
        
        // 日期类型比较
        if (value1 instanceof Date && value2 instanceof Date) {
            return ((Date) value1).compareTo((Date) value2);
        }
        
        // 字符串比较
        return value1.toString().compareTo(value2.toString());
    }
    
    /**
     * 创建排序比较器
     */
    private Comparator<Map<String, Object>> createComparator(Map<String, Boolean> sortOrders) {
        if (sortOrders == null || sortOrders.isEmpty()) {
            // 默认按创建时间倒序
            return Comparator.comparing(
                (Map<String, Object> m) -> {
                    Object createdAt = m.get("createdAt");
                    return createdAt != null ? (Date) createdAt : new Date(0);
                }, 
                Comparator.nullsLast(Date::compareTo)
            ).reversed();
        }
        
        Comparator<Map<String, Object>> comparator = null;
        
        for (Map.Entry<String, Boolean> entry : sortOrders.entrySet()) {
            String field = entry.getKey();
            boolean ascending = entry.getValue();
            
            Comparator<Map<String, Object>> fieldComparator = Comparator.comparing(
                (Map<String, Object> m) -> m.get(field), 
                Comparator.nullsLast((v1, v2) -> {
                    if (v1 == v2) return 0;
                    if (v1 == null) return ascending ? -1 : 1;
                    if (v2 == null) return ascending ? 1 : -1;
                    
                    return ascending ? compare(v1, v2) : -compare(v1, v2);
                })
            );
            
            comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
        }
        
        return comparator;
    }
    
    /**
     * 获取下一个版本号
     */
    private long getNextVersion(String modelName) {
        return versionCounters.computeIfAbsent(modelName, k -> new AtomicLong(0)).incrementAndGet();
    }
}