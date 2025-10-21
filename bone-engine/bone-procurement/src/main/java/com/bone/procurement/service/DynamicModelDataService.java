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
     * 创建动态模型数据 - 支持四阶驱动模型的完整生命周期管理
     */
    public Map<String, Object> createData(String modelName, Map<String, Object> data) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(data, "数据不能为空");
        
        // 获取元数据
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 生成唯一ID
        String id = UUID.randomUUID().toString();
        Map<String, Object> processedData = new HashMap<>(data);
        processedData.put("id", id);
        
        // 设置创建时间和创建者信息
        long timestamp = System.currentTimeMillis();
        processedData.put("createdAt", timestamp);
        processedData.put("updatedAt", timestamp);
        processedData.put("createdBy", "current_user"); // 在实际实现中应使用当前用户
        processedData.put("updatedBy", "current_user");
        
        // 设置版本号（乐观锁）
        processedData.put("version", 1L);
        
        // 设置租户ID - 支持多租户隔离
        processedData.put("tenantId", "current_tenant"); // 在实际实现中应使用租户上下文

        // 初始化模型数据存储（如果不存在）
        modelDataStore.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>());
        versionCounters.computeIfAbsent(modelName, k -> new AtomicLong(0));

        // 处理计算字段和业务规则
        try {
            processedData = metadataEngine.processEntityInstance(modelName, processedData);
        } catch (Exception e) {
            log.error("创建实体时出错: {}", modelName, e);
            throw new BusinessException("创建数据失败: " + e.getMessage());
        }

        // 存储数据
        modelDataStore.get(modelName).put(id, processedData);

        log.info("创建动态模型数据成功: {}, ID: {}, 版本: 1", modelName, id);
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
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
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
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
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
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
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
     * 更新动态模型数据
     */
    public Map<String, Object> updateData(String modelName, String id, Map<String, Object> data) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "数据ID不能为空");
        Assert.notNull(data, "数据不能为空");
        
        // 获取元数据
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }
        
        // 检查数据是否存在
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null || !modelData.containsKey(id)) {
            throw new BusinessException("数据不存在: " + id);
        }
        
        Map<String, Object> existingData = new HashMap<>(modelData.get(id));
        
        // 检查版本号，实现乐观锁
        Long currentVersion = (Long) existingData.get("version");
        Long newVersion = data.get("version") != null ? Long.valueOf(data.get("version").toString()) : null;
        
        if (newVersion == null || !newVersion.equals(currentVersion)) {
            throw new BusinessException("数据版本冲突，请刷新后重试。当前版本: " + currentVersion + ", 提交版本: " + newVersion);
        }
        
        // 保存历史版本
        saveHistoryVersion(modelName, id, existingData);
        
        // 创建新的数据对象，保留不变的字段
        Map<String, Object> updatedData = new HashMap<>(existingData);
        
        // 移除版本号字段，因为将由系统更新
        data.remove("version");
        
        // 更新字段（排除不可修改字段）
        data.forEach((key, value) -> {
            if (!"id".equals(key) && !"createdAt".equals(key) && !"createdBy".equals(key)) {
                updatedData.put(key, value);
            }
        });
        
        // 更新时间戳和更新者信息
        updatedData.put("updatedAt", System.currentTimeMillis());
        updatedData.put("updatedBy", "current_user"); // 在实际实现中应使用当前用户
        
        // 递增版本号
        updatedData.put("version", currentVersion + 1);
        
        // 处理计算字段和业务规则
        try {
            updatedData = metadataEngine.processEntityInstance(modelName, new HashMap<>(updatedData));
        } catch (Exception e) {
            log.error("更新实体时出错: {}, ID: {}", modelName, id, e);
            throw new BusinessException("更新数据失败: " + e.getMessage());
        }
        
        // 更新数据
        modelData.put(id, updatedData);
        
        log.info("更新动态模型数据成功: {}, ID: {}, 新版本: {}", 
                modelName, id, updatedData.get("version"));
        return new HashMap<>(updatedData); // 返回副本，避免外部修改
    }
    
    /**
     * 更新动态模型数据（支持上下文参数）
     */
    public Map<String, Object> updateData(String modelName, String id, Map<String, Object> data, Map<String, Object> context) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "数据ID不能为空");
        Assert.notNull(data, "数据不能为空");
        
        // 获取元数据
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }
        
        // 检查数据是否存在
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null || !modelData.containsKey(id)) {
            throw new BusinessException("数据不存在: " + id);
        }
        
        Map<String, Object> existingData = new HashMap<>(modelData.get(id));
        
        // 检查版本号，实现乐观锁
        Long currentVersion = (Long) existingData.get("version");
        Long newVersion = data.get("version") != null ? Long.valueOf(data.get("version").toString()) : null;
        
        if (newVersion == null || !newVersion.equals(currentVersion)) {
            throw new BusinessException("数据版本冲突，请刷新后重试。当前版本: " + currentVersion + ", 提交版本: " + newVersion);
        }
        
        // 保存历史版本
        saveHistoryVersion(modelName, id, existingData);
        
        // 创建新的数据对象，保留不变的字段
        Map<String, Object> updatedData = new HashMap<>(existingData);
        
        // 移除版本号字段，因为将由系统更新
        data.remove("version");
        
        // 更新字段（排除不可修改字段）
        data.forEach((key, value) -> {
            if (!"id".equals(key) && !"createdAt".equals(key) && !"createdBy".equals(key)) {
                updatedData.put(key, value);
            }
        });
        
        // 更新时间戳和更新者信息
        updatedData.put("updatedAt", System.currentTimeMillis());
        updatedData.put("updatedBy", context != null && context.get("userId") != null 
                ? context.get("userId") : "current_user");
        
        // 递增版本号
        updatedData.put("version", currentVersion + 1);
        
        // 处理计算字段和业务规则
        try {
            // 如果提供了上下文参数，则将其传递给元数据引擎
            if (context != null) {
                updatedData.putAll(context);
            }
            
            updatedData = metadataEngine.processEntityInstance(modelName, new HashMap<>(updatedData));
            
        } catch (Exception e) {
            log.error("更新实体时出错: {}, ID: {}", modelName, id, e);
            throw new BusinessException("更新数据失败: " + e.getMessage());
        }
        
        // 更新数据
        modelData.put(id, updatedData);
        
        log.info("更新动态模型数据成功: {}, ID: {}, 新版本: {}", 
                modelName, id, updatedData.get("version"));
        return new HashMap<>(updatedData); // 返回副本，避免外部修改
    }

    /**
     * 删除动态模型数据（带软删除功能）- 支持四阶驱动模型的完整生命周期管理和业务规则应用
     */
    public boolean deleteData(String modelName, String id) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(id, "数据ID不能为空");
        
        // 获取元数据
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 检查数据是否存在
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null || !modelData.containsKey(id)) {
            throw new BusinessException("数据不存在: " + id);
        }
        
        // 获取要删除的数据
        Map<String, Object> dataToDelete = new HashMap<>(modelData.get(id));
        
        // 检查是否已被删除
        Boolean alreadyDeleted = (Boolean) dataToDelete.get("deleted");
        if (alreadyDeleted != null && alreadyDeleted) {
            log.info("数据已被删除: {}, ID: {}", modelName, id);
            return true; // 已删除则直接返回成功
        }
        
        // 保存历史版本用于恢复
        saveHistoryVersion(modelName, id, dataToDelete);
        
        // 处理删除前的业务规则
        try {
            // 使用HashMap包装数据以确保可修改
            metadataEngine.processEntityInstance(modelName, new HashMap<>(dataToDelete));
        } catch (Exception e) {
            log.error("处理删除操作时出错: {}, ID: {}", modelName, id, e);
            throw new BusinessException("删除数据失败: " + e.getMessage());
        }
        
        // 软删除实现：标记删除
        dataToDelete.put("deleted", true);
        dataToDelete.put("deletedAt", System.currentTimeMillis());
        dataToDelete.put("deletedBy", "current_user"); // 在实际实现中应使用当前用户
        dataToDelete.put("version", ((Long) dataToDelete.getOrDefault("version", 1L)) + 1);
        modelData.put(id, dataToDelete);
        
        log.info("删除动态模型数据成功: {}, ID: {}, 版本: {}", 
                modelName, id, dataToDelete.get("version"));
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
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
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
    
    log.info("开始查询动态模型数据: {}, 条件: {}, 页码: {}, 页大小: {}", 
            modelName, queryConditions, page, pageSize);
    
    try {
        // 获取元数据
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }

        // 获取数据
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null || modelData.isEmpty()) {
            log.info("查询动态模型数据: {}, 模型无数据存在", modelName);
            return Collections.emptyList();
        }

        // 过滤数据（排除已软删除的数据）
        List<Map<String, Object>> filteredData = modelData.values().stream()
                .filter(data -> !Boolean.TRUE.equals(data.get("deleted")))
                .filter(data -> matchesConditions(data, queryConditions))
                .collect(Collectors.toList());
        
        int totalFiltered = filteredData.size();
        log.debug("查询过滤后数据总量: {}, 模型: {}", totalFiltered, modelName);
        
        // 应用排序
        if (sortOrders != null && !sortOrders.isEmpty()) {
            try {
                filteredData.sort((data1, data2) -> {
                    for (Map.Entry<String, Boolean> sortOrder : sortOrders.entrySet()) {
                        String field = sortOrder.getKey();
                        boolean ascending = sortOrder.getValue();
                        
                        Object value1 = data1.getOrDefault(field, null);
                        Object value2 = data2.getOrDefault(field, null);
                        
                        if (value1 == null && value2 == null) {
                            continue;
                        } else if (value1 == null) {
                            return ascending ? -1 : 1;
                        } else if (value2 == null) {
                            return ascending ? 1 : -1;
                        }
                        
                        if (value1 instanceof Comparable && value2 instanceof Comparable) {
                            try {
                                // 确保类型相同才进行比较
                                if (value1.getClass().equals(value2.getClass())) {
                                    @SuppressWarnings("unchecked")
                                    Comparable<Object> comp1 = (Comparable<Object>) value1;
                                    int compareResult = comp1.compareTo(value2);
                                    if (compareResult != 0) {
                                        return ascending ? compareResult : -compareResult;
                                    }
                                } else {
                                    // 尝试字符串比较作为后备方案
                                    String str1 = value1.toString();
                                    String str2 = value2.toString();
                                    int compareResult = str1.compareTo(str2);
                                    if (compareResult != 0) {
                                        return ascending ? compareResult : -compareResult;
                                    }
                                }
                            } catch (ClassCastException e) {
                                log.warn("无法比较的值类型: {}, {}, 字段: {}", 
                                        value1.getClass(), value2.getClass(), field);
                                // 发生异常时视为相等
                            }
                        }
                    }
                    return 0;
                });
            } catch (Exception e) {
                log.error("排序数据时出错: {}, 模型: {}", e.getMessage(), modelName, e);
                // 排序失败时继续执行，返回未排序的数据
            }
        }
        
        // 应用分页
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filteredData.size());
        
        List<Map<String, Object>> paginatedData;
        if (startIndex >= filteredData.size()) {
            paginatedData = Collections.emptyList();
        } else {
            paginatedData = filteredData.subList(startIndex, endIndex);
        }
        
        log.debug("分页后数据量: {}, 起始索引: {}, 结束索引: {}", 
                paginatedData.size(), startIndex, endIndex);
        
        // 处理计算字段和虚拟字段
        List<Map<String, Object>> processedData = paginatedData.stream()
                .map(data -> {
                    try {
                        return metadataEngine.processEntityInstance(modelName, new HashMap<>(data));
                    } catch (Exception e) {
                        log.error("处理查询结果实体时出错: {}, ID: {}", modelName, data.get("id"), e);
                        return new HashMap<>(data); // 出错时返回原始数据副本
                    }
                })
                .collect(Collectors.toList());
        
        // 记录查询日志
        int totalPages = totalFiltered > 0 ? (totalFiltered + pageSize - 1) / pageSize : 0;
        log.info("查询动态模型数据成功: {}, 条件过滤后总数: {}, 返回数据量: {}, 页码: {}/{}, 排序字段: {}", 
                modelName, totalFiltered, processedData.size(), page, totalPages, sortOrders);
                
        return processedData;
    } catch (BusinessException e) {
        log.error("业务异常: 查询动态模型数据失败 - {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("查询动态模型数据时发生未预期的错误: {}, 模型: {}", e.getMessage(), modelName, e);
        throw new BusinessException("查询数据失败: " + e.getMessage());
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
     * 查询动态模型数据的简化版本（支持分页）
     */
    public List<Map<String, Object>> queryData(String modelName, Map<String, Object> conditions, Integer page, Integer pageSize) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        
        log.info("开始简化分页查询动态模型数据: {}, 条件: {}, 页码: {}, 页大小: {}", 
                modelName, conditions, page, pageSize);
        
        try {
            return queryData(modelName, conditions, page != null ? page : 1, 
                    pageSize != null ? pageSize : Integer.MAX_VALUE, null);
        } catch (BusinessException e) {
            log.error("业务异常: 简化分页查询动态模型数据失败 - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("简化分页查询动态模型数据时发生未预期的错误: {}, 模型: {}", e.getMessage(), modelName, e);
            throw new BusinessException("查询数据失败: " + e.getMessage());
        }
    }

    /**
     * 检查数据是否匹配查询条件（支持复杂条件）
     */
    private boolean matchesConditions(Map<String, Object> data, Map<String, Object> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        
        if (data == null) {
            return false;
        }
        
        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            String fieldName = condition.getKey();
            Object expectedValue = condition.getValue();
            
            try {
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
            } catch (Exception e) {
                log.error("条件匹配时发生错误: fieldName={}, condition={}", fieldName, expectedValue, e);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 使用指定操作符检查条件匹配
     */
    private boolean matchesConditionWithOperator(Map<String, Object> data, String fieldName, 
                                               Object expectedValue, QueryOperator operator) {
        if (data == null || fieldName == null || operator == null) {
            return false;
        }
        
        try {
            // 检查字段是否存在
            boolean fieldExists = data.containsKey(fieldName);
            Object actualValue = data.get(fieldName);
            
            switch (operator) {
                case EQ:
                    return safeEquals(actualValue, expectedValue);
                case NEQ:
                    return !safeEquals(actualValue, expectedValue);
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
                        // 简单实现，支持百分号通配符
                        String pattern = ((String) expectedValue).replace("%", ".*");
                        return java.util.regex.Pattern.matches(pattern, (String) actualValue);
                    }
                    return false;
                case IN:
                    if (expectedValue instanceof Collection) {
                        return ((Collection<?>) expectedValue).contains(actualValue);
                    }
                    if (expectedValue != null && expectedValue.getClass().isArray()) {
                        return java.util.Arrays.asList((Object[]) expectedValue).contains(actualValue);
                    }
                    return false;
                case NOT_IN:
                    if (expectedValue instanceof Collection) {
                        return !((Collection<?>) expectedValue).contains(actualValue);
                    }
                    if (expectedValue != null && expectedValue.getClass().isArray()) {
                        return !java.util.Arrays.asList((Object[]) expectedValue).contains(actualValue);
                    }
                    return true;
                case IS_NULL:
                    return actualValue == null;
                case IS_NOT_NULL:
                    return actualValue != null;
                default:
                    return false;
            }
        } catch (Exception e) {
            log.error("条件操作符匹配时发生错误: operator={}, fieldName={}, dataValue={}, conditionValue={}", 
                    operator, fieldName, data.get(fieldName), expectedValue, e);
            return false;
        }
    }
    
    /**
     * 安全比较两个值
     */
    private boolean safeEquals(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }
        
        // 处理数值类型的比较，允许不同数值类型之间的比较
        if (isNumber(obj1) && isNumber(obj2)) {
            double d1 = ((Number) obj1).doubleValue();
            double d2 = ((Number) obj2).doubleValue();
            return Double.compare(d1, d2) == 0;
        }
        
        // 尝试直接比较
        try {
            return obj1.equals(obj2);
        } catch (Exception e) {
            log.warn("对象比较异常: {} and {}", obj1, obj2, e);
            return false;
        }
    }
    
    /**
     * 判断对象是否为数值类型
     */
    private boolean isNumber(Object obj) {
        return obj instanceof Number;
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
        
        // 安全处理不同类型的比较
        if (value1 instanceof Comparable && value2 instanceof Comparable) {
            try {
                // 确保类型相同才进行比较
                if (value1.getClass().equals(value2.getClass())) {
                    @SuppressWarnings("unchecked")
                    Comparable<Object> comparable1 = (Comparable<Object>) value1;
                    return comparable1.compareTo(value2);
                } else {
                    // 尝试字符串比较作为后备方案
                    String str1 = value1.toString();
                    String str2 = value2.toString();
                    return str1.compareTo(str2);
                }
            } catch (ClassCastException e) {
                log.warn("无法比较的值类型: {}, {}", value1.getClass(), value2.getClass());
                return 0;
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
        Assert.hasText(id, "数据ID不能为空");
        
        log.info("开始恢复动态模型数据版本: {}, ID: {}, 目标版本索引: {}", modelName, id, versionIndex);
        
        // 获取元数据
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }
        
        // 获取历史数据
        Map<String, List<Map<String, Object>>> modelHistory = historyStore.get(modelName);
        if (modelHistory == null) {
            throw new BusinessException("没有找到历史记录");
        }
        
        List<Map<String, Object>> history = modelHistory.get(id);
        if (history == null) {
            throw new BusinessException("找不到该数据的历史记录: " + id);
        }
        
        if (versionIndex < 0 || versionIndex >= history.size()) {
            throw new BusinessException("指定的历史版本索引无效: " + versionIndex + ", 有效范围: 0-" + (history.size() - 1));
        }
        
        // 获取数据存储
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null || !modelData.containsKey(id)) {
            throw new BusinessException("数据不存在: " + id);
        }
        
        // 获取当前数据
        Map<String, Object> currentData = new HashMap<>(modelData.get(id));
        
        // 获取要恢复的历史版本数据
        Map<String, Object> historicalData = new HashMap<>(history.get(versionIndex));
        
        // 保存当前版本到历史
        saveHistoryVersion(modelName, id, currentData);
        
        // 恢复版本 - 只恢复核心业务字段，保留系统字段
        Map<String, Object> restoredData = new HashMap<>();
        
        // 保留必要的系统字段
        restoredData.put("id", id);
        restoredData.put("createdAt", currentData.get("createdAt"));
        restoredData.put("createdBy", currentData.get("createdBy"));
        restoredData.put("tenantId", currentData.getOrDefault("tenantId", "current_tenant"));
        
        // 复制历史版本中的业务字段（排除系统字段）
        for (Map.Entry<String, Object> entry : historicalData.entrySet()) {
            String key = entry.getKey();
            if (!"id".equals(key) && !"createdAt".equals(key) && !"createdBy".equals(key) &&
                !"updatedAt".equals(key) && !"updatedBy".equals(key) && !"version".equals(key) &&
                !"deleted".equals(key) && !"deletedAt".equals(key) && !"deletedBy".equals(key) &&
                !"tenantId".equals(key)) {
                restoredData.put(key, entry.getValue());
            }
        }
        
        // 更新恢复后的元数据
        restoredData.put("updatedAt", System.currentTimeMillis());
        restoredData.put("updatedBy", "current_user"); // 在实际实现中应使用当前用户
        
        // 移除删除标记（如果有）
        restoredData.remove("deleted");
        restoredData.remove("deletedAt");
        restoredData.remove("deletedBy");
        
        // 生成新版本号
        Long currentVersion = (Long) currentData.getOrDefault("version", 1L);
        restoredData.put("version", currentVersion + 1);
        
        // 处理恢复后的实体
        try {
            restoredData = metadataEngine.processEntityInstance(modelName, new HashMap<>(restoredData));
        } catch (Exception e) {
            log.error("处理恢复的实体时出错: {}, ID: {}, 版本索引: {}", modelName, id, versionIndex, e);
            throw new BusinessException("恢复版本失败: " + e.getMessage());
        }
        
        // 存储恢复后的数据
        modelData.put(id, restoredData);
        
        log.info("恢复动态模型数据版本成功: {}, ID: {}, 版本索引: {}, 新版本: {}", 
                modelName, id, versionIndex, restoredData.get("version"));
        return new HashMap<>(restoredData); // 返回副本避免外部修改
    }
    
    /**
     * 批量创建动态模型数据
     */
    public List<Map<String, Object>> batchCreateData(String modelName, List<Map<String, Object>> dataList) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(dataList, "数据列表不能为空");
        Assert.isTrue(!dataList.isEmpty(), "数据列表不能为空");
        Assert.isTrue(dataList.size() <= 1000, "批量创建数据量不能超过1000条");

        log.info("开始批量创建动态模型数据: {}, 数据量: {}", modelName, dataList.size());

        // 获取元数据
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }
        
        // 初始化模型数据存储（如果不存在）
        Map<String, Map<String, Object>> modelData = modelDataStore.computeIfAbsent(modelName, 
                k -> new ConcurrentHashMap<>());
        versionCounters.computeIfAbsent(modelName, k -> new AtomicLong(0));
        
        // 并发安全的计数器和集合
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Map<String, Object>> results = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> failedIds = new ConcurrentLinkedQueue<>();
        long timestamp = System.currentTimeMillis();
        
        // 批量处理数据 - 并行处理提高性能
        dataList.parallelStream().forEach(data -> {
            try {
                // 验证单条数据不能为空
                if (data == null || data.isEmpty()) {
                    log.warn("批量创建时发现空数据");
                    failCount.incrementAndGet();
                    failedIds.add("空数据");
                    return;
                }
                
                // 创建数据副本以避免修改原始数据
                Map<String, Object> processedData = new HashMap<>(data);
                
                // 生成ID（如果没有提供）
                String id = processedData.get("id") != null ? processedData.get("id").toString() : UUID.randomUUID().toString();
                processedData.put("id", id);
                
                // 检查ID是否已存在
                if (modelData.containsKey(id)) {
                    log.warn("批量创建时发现重复ID: {}", id);
                    failCount.incrementAndGet();
                    failedIds.add(id);
                    return;
                }
                
                // 设置创建时间和创建者信息
                processedData.put("createdAt", timestamp);
                processedData.put("updatedAt", timestamp);
                processedData.put("createdBy", "current_user");
                processedData.put("updatedBy", "current_user");
                
                // 设置版本号和租户ID
                processedData.put("version", 1L);
                processedData.put("tenantId", "current_tenant");
                
                // 确保没有删除标记
                processedData.remove("deleted");
                processedData.remove("deletedAt");
                processedData.remove("deletedBy");
                
                // 处理计算字段和业务规则
                try {
                    processedData = metadataEngine.processEntityInstance(modelName, processedData);
                } catch (Exception e) {
                    log.error("批量创建数据处理时出错: {}, ID: {}", modelName, id, e);
                    throw new BusinessException("批量创建数据失败: " + e.getMessage());
                }
                
                // 存储数据
                modelData.put(id, processedData);
                results.add(new HashMap<>(processedData));
                successCount.incrementAndGet();
                
            } catch (BusinessException e) {
                String id = data != null && data.get("id") != null ? data.get("id").toString() : "未知ID";
                log.error("批量创建数据业务异常: {}, ID: {}, 错误: {}", modelName, id, e.getMessage());
                failCount.incrementAndGet();
                failedIds.add(id);
            } catch (Exception e) {
                String id = data != null && data.get("id") != null ? data.get("id").toString() : "未知ID";
                log.error("批量创建实体时出错: {}, ID: {}", modelName, id, e);
                failCount.incrementAndGet();
                failedIds.add(id);
            }
        });
        
        // 记录详细日志
        log.info("批量创建动态模型数据完成: {}, 总数据量: {}, 成功: {}, 失败: {}",
                modelName, dataList.size(), successCount.get(), failCount.get());
        
        if (!failedIds.isEmpty()) {
            log.warn("部分数据创建失败，失败ID: {}, 数量: {}", failedIds, failedIds.size());
        }
        
        return new ArrayList<>(results);
    }
    
    /**
     * 批量删除动态模型数据 - 支持四阶驱动模型的批量处理特性
     */
    public int batchDeleteData(String modelName, List<String> ids) {
        // 参数校验
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(ids, "ID列表不能为空");
        Assert.isTrue(!ids.isEmpty(), "ID列表不能为空");

        log.info("批量删除动态模型数据: {}, 数量: {}", modelName, ids.size());

        // 获取元数据
        EntityMetadata entityMetadata = dynamicModelManager.getEntityMetadata(modelName);
        if (entityMetadata == null) {
            throw new BusinessException("动态模型不存在: " + modelName);
        }
        
        // 检查数据存储是否存在
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null) {
            log.warn("模型数据存储不存在: {}", modelName);
            return 0;
        }
        
        AtomicLong deletedCount = new AtomicLong(0);
        List<String> failedIds = Collections.synchronizedList(new ArrayList<>());
        
        // 批量删除数据 - 并行处理提高性能
        ids.parallelStream().forEach(id -> {
            try {
                // 检查数据是否存在
                if (!modelData.containsKey(id)) {
                    log.warn("要删除的数据不存在: {}, ID: {}", modelName, id);
                    failedIds.add(id);
                    return;
                }
                
                // 获取要删除的数据
                Map<String, Object> dataToDelete = new HashMap<>(modelData.get(id));
                
                // 检查是否已被删除
                Boolean alreadyDeleted = (Boolean) dataToDelete.get("deleted");
                if (alreadyDeleted != null && alreadyDeleted) {
                    log.info("数据已被删除: {}, ID: {}", modelName, id);
                    return;
                }
                
                // 保存历史版本用于恢复
                saveHistoryVersion(modelName, id, dataToDelete);
                
                // 处理删除前的业务规则
                try {
                    metadataEngine.processEntityInstance(modelName, new HashMap<>(dataToDelete));
                } catch (Exception e) {
                    log.error("处理删除操作时出错: {}, ID: {}", modelName, id, e);
                    failedIds.add(id);
                    return; // 继续处理其他数据
                }
                
                // 执行软删除
                dataToDelete.put("deleted", true);
                dataToDelete.put("deletedAt", System.currentTimeMillis());
                dataToDelete.put("deletedBy", "current_user");
                dataToDelete.put("version", ((Long) dataToDelete.getOrDefault("version", 1L)) + 1);
                modelData.put(id, dataToDelete);
                deletedCount.incrementAndGet();
            } catch (Exception e) {
                log.error("批量删除实体时出错: {}, ID: {}", modelName, id, e);
                failedIds.add(id);
            }
        });
        
        if (!failedIds.isEmpty()) {
            log.warn("部分数据删除失败: {}, 失败ID数量: {}", modelName, failedIds.size());
        }
        
        log.info("批量删除完成: {}, 成功删除: {}, 失败: {}", 
                modelName, deletedCount.get(), failedIds.size());
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