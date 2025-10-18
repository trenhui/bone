package com.bone.procurement.service;

import com.bone.procurement.config.DynamicModelConfig;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 动态模型数据服务
 * 负责动态模型的数据操作（增删改查）
 */
@Service
public class DynamicModelDataService {

    private static final Logger log = LoggerFactory.getLogger(DynamicModelDataService.class);

    @Autowired
    private DynamicModelManager dynamicModelManager;

    @Autowired
    private DynamicModelConfig dynamicModelConfig;

    // 模拟数据库存储
    private final Map<String, Map<String, Map<String, Object>>> modelDataStore = new ConcurrentHashMap<>();

    /**
     * 创建动态模型数据
     */
    public Map<String, Object> createData(String modelName, Map<String, Object> data) {
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new IllegalArgumentException("动态模型不存在: " + modelName);
        }

        // 验证数据
        validateData(entityMetadata, data);

        // 生成唯一ID
        String id = UUID.randomUUID().toString();
        data.put("id", id);

        // 初始化模型数据存储（如果不存在）
        modelDataStore.computeIfAbsent(modelName, k -> new ConcurrentHashMap<>());

        // 存储数据
        modelDataStore.get(modelName).put(id, new HashMap<>(data));

        log.info("创建动态模型数据成功: {}, ID: {}", modelName, id);
        return data;
    }

    /**
     * 根据ID获取动态模型数据
     */
    public Map<String, Object> getDataById(String modelName, String id) {
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new IllegalArgumentException("动态模型不存在: " + modelName);
        }

        // 验证ID
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID不能为空");
        }

        // 获取数据
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null) {
            return null;
        }

        return modelData.get(id);
    }

    /**
     * 获取动态模型的所有数据
     */
    public List<Map<String, Object>> getAllData(String modelName) {
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new IllegalArgumentException("动态模型不存在: " + modelName);
        }

        // 获取数据
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(modelData.values());
    }

    /**
     * 更新动态模型数据
     */
    public Map<String, Object> updateData(String modelName, String id, Map<String, Object> data) {
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new IllegalArgumentException("动态模型不存在: " + modelName);
        }

        // 验证ID
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID不能为空");
        }

        // 检查数据是否存在
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null || !modelData.containsKey(id)) {
            throw new IllegalArgumentException("数据不存在: " + id);
        }

        // 确保ID不被修改
        data.put("id", id);

        // 验证数据
        validateData(entityMetadata, data);

        // 更新数据
        modelData.put(id, new HashMap<>(data));

        log.info("更新动态模型数据成功: {}, ID: {}", modelName, id);
        return data;
    }

    /**
     * 删除动态模型数据
     */
    public boolean deleteData(String modelName, String id) {
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new IllegalArgumentException("动态模型不存在: " + modelName);
        }

        // 验证ID
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID不能为空");
        }

        // 检查数据是否存在
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null) {
            return false;
        }

        // 删除数据
        boolean removed = modelData.remove(id) != null;

        if (removed) {
            log.info("删除动态模型数据成功: {}, ID: {}", modelName, id);
        }

        return removed;
    }

    /**
     * 查询动态模型数据（简单条件查询）
     */
    public List<Map<String, Object>> queryData(String modelName, Map<String, Object> queryConditions) {
        // 验证模型是否存在
        EntityMetadata entityMetadata = dynamicModelManager.getModelByName(modelName);
        if (entityMetadata == null) {
            throw new IllegalArgumentException("动态模型不存在: " + modelName);
        }

        // 获取数据
        Map<String, Map<String, Object>> modelData = modelDataStore.get(modelName);
        if (modelData == null) {
            return Collections.emptyList();
        }

        // 如果没有查询条件，返回所有数据
        if (queryConditions == null || queryConditions.isEmpty()) {
            return new ArrayList<>(modelData.values());
        }

        // 根据条件过滤数据
        return modelData.values().stream()
                .filter(data -> matchesConditions(data, queryConditions))
                .collect(Collectors.toList());
    }

    /**
     * 验证数据是否符合模型定义
     */
    private void validateData(EntityMetadata entityMetadata, Map<String, Object> data) {
        // 获取所有必填字段
        Set<String> requiredFields = entityMetadata.getFields().values().stream()
                .filter(FieldMetadata::isRequired)
                .map(FieldMetadata::getApiName)
                .collect(Collectors.toSet());

        // 检查必填字段是否都存在
        for (String requiredField : requiredFields) {
            if (!data.containsKey(requiredField)) {
                throw new IllegalArgumentException("缺少必填字段: " + requiredField);
            }
        }

        // 验证字段类型和约束
        for (Map.Entry<String, FieldMetadata> fieldEntry : entityMetadata.getFields().entrySet()) {
            String fieldName = fieldEntry.getKey();
            FieldMetadata field = fieldEntry.getValue();

            if (data.containsKey(fieldName)) {
                Object value = data.get(fieldName);
                validateFieldValue(field, value);
            }
        }
    }

    /**
     * 验证字段值是否符合字段定义
     */
    private void validateFieldValue(FieldMetadata field, Object value) {
        // 验证字段类型
        String fieldType = field.getType();
        if (value != null) {
            switch (fieldType) {
                case "string":
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException("字段类型错误，期望String: " + field.getApiName());
                    }
                    String stringValue = (String) value;
                    // 验证最大长度
                    if (field.getMaxLength() != null && stringValue.length() > field.getMaxLength()) {
                        throw new IllegalArgumentException("字段长度超出限制: " + field.getApiName());
                    }
                    break;
                case "integer":
                    if (!(value instanceof Integer || value instanceof String)) {
                        throw new IllegalArgumentException("字段类型错误，期望Integer: " + field.getApiName());
                    }
                    break;
                case "double":
                    if (!(value instanceof Double || value instanceof Integer || value instanceof String)) {
                        throw new IllegalArgumentException("字段类型错误，期望Double: " + field.getApiName());
                    }
                    break;
                case "boolean":
                    if (!(value instanceof Boolean || value instanceof String)) {
                        throw new IllegalArgumentException("字段类型错误，期望Boolean: " + field.getApiName());
                    }
                    break;
                case "date":
                    if (!(value instanceof Date || value instanceof String)) {
                        throw new IllegalArgumentException("字段类型错误，期望Date: " + field.getApiName());
                    }
                    break;
            }
        }
    }

    /**
     * 检查数据是否匹配查询条件
     */
    private boolean matchesConditions(Map<String, Object> data, Map<String, Object> conditions) {
        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            String fieldName = condition.getKey();
            Object expectedValue = condition.getValue();
            
            if (!data.containsKey(fieldName)) {
                return false;
            }
            
            Object actualValue = data.get(fieldName);
            if (!Objects.equals(actualValue, expectedValue)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * 清空指定模型的数据
     */
    public void clearModelData(String modelName) {
        if (modelDataStore.containsKey(modelName)) {
            modelDataStore.get(modelName).clear();
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