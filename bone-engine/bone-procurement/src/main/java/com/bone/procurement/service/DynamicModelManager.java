package com.bone.procurement.service;

import com.bone.procurement.config.DynamicModelConfig;
import com.bone.procurement.exception.BusinessException;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 动态模型管理器
 * 负责基于元数据引擎的动态模型加载、注册和管理
 * 遵循业界最佳实践，如Workday、Salesforce和Coupa的元数据驱动设计
 */
@Service
public class DynamicModelManager implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DynamicModelManager.class);

    @Autowired
    private DynamicModelConfig dynamicModelConfig;

    // 移除对不存在的MetadataEngine的依赖
    private Map<String, Object> modelsCache = new ConcurrentHashMap<>();

    /**
     * 初始化方法，在应用启动时加载所有动态模型
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("开始初始化动态模型管理器...");
        
        // 初始化模型缓存
        this.modelsCache = new ConcurrentHashMap<>();
        
        // 加载所有动态模型配置
        if (dynamicModelConfig.isEnabled()) {
            loadAllDynamicModels();
        }
        
        log.info("动态模型管理器初始化完成");
    }

    /**
     * 加载所有配置的动态模型
     */
    public void loadAllDynamicModels() {
        try {
            // 确保dynamicModelConfig不为空
            if (dynamicModelConfig == null || dynamicModelConfig.getModels() == null || dynamicModelConfig.getModels().isEmpty()) {
                log.warn("未找到配置的动态模型，跳过加载");
                return;
            }
            
            log.info("开始加载动态模型，共 {} 个模型", dynamicModelConfig.getModels().size());
            
            // 遍历所有模型定义并注册
            for (Map.Entry<String, DynamicModelConfig.DynamicModelDefinition> entry : dynamicModelConfig.getModels().entrySet()) {
                String modelName = entry.getKey();
                DynamicModelConfig.DynamicModelDefinition definition = entry.getValue();
                
                log.info("加载动态模型: {}, 标签: {}", modelName, definition.getLabel());
                
                // 创建简化的实体元数据
                Map<String, Object> entityMetadata = createEntityMetadata(modelName, definition);
                
                // 注册模型
                registerModel(entityMetadata);
                
                // 记录成功加载的日志
                Map<String, Object> fields = (Map<String, Object>) entityMetadata.get("fields");
                log.info("成功加载动态模型: {}, 字段数量: {}", 
                        modelName, 
                        fields != null ? fields.size() : 0);
            }
            
            log.info("共加载 {} 个动态模型", dynamicModelConfig.getModels().size());
            
        } catch (Exception e) {
            log.error("加载动态模型失败", e);
            throw new BusinessException("加载动态模型失败: " + e.getMessage(), "MODEL_LOAD_FAILED");
        }
    }

    /**
     * 创建实体元数据 - 支持四阶驱动模型的标准字段
     */
    
    
    // 简化实现，返回基础对象代替EntityMetadata
    private Map<String, Object> createEntityMetadata(String modelName, 
                                              DynamicModelConfig.DynamicModelDefinition definition) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(definition, "模型定义不能为空");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("apiName", modelName);
        metadata.put("label", definition.getLabel() != null ? definition.getLabel() : modelName);
        metadata.put("description", definition.getDescription());
        metadata.put("domain", "procurement");
        
        // 简化实现，不使用FieldMetadata类
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", Map.of("name", "id", "type", "string")); // 简单的Map代替FieldMetadata
        
        // 添加标准版本管理字段 - 简化实现
        fields.put("version", Map.of("name", "version", "type", "integer"));
        
        // 添加标准审计字段 - 简化实现
        fields.put("createdAt", Map.of("name", "createdAt", "type", "datetime"));
        fields.put("createdBy", Map.of("name", "createdBy", "type", "string"));
        fields.put("updatedAt", Map.of("name", "updatedAt", "type", "datetime"));
        fields.put("updatedBy", Map.of("name", "updatedBy", "type", "string"));
        
        // 添加软删除字段 - 简化实现
        fields.put("deletedAt", Map.of("name", "deletedAt", "type", "datetime"));
        fields.put("deletedBy", Map.of("name", "deletedBy", "type", "string"));
        fields.put("isDeleted", Map.of("name", "isDeleted", "type", "boolean"));
        
        // 转换配置中的字段定义为简化的字段信息
        if (definition.getFields() != null) {
            for (Map.Entry<String, DynamicModelConfig.DynamicFieldDefinition> fieldEntry : definition.getFields().entrySet()) {
                String fieldName = fieldEntry.getKey();
                DynamicModelConfig.DynamicFieldDefinition fieldDef = fieldEntry.getValue();
                
                // 避免覆盖标准字段
                if (!fields.containsKey(fieldName)) {
                    // 简化的字段信息
                    Map<String, Object> fieldInfo = new HashMap<>();
                    fieldInfo.put("name", fieldName);
                    fieldInfo.put("type", fieldDef.getType());
                    fieldInfo.put("label", fieldDef.getLabel() != null ? fieldDef.getLabel() : fieldName);
                    fields.put(fieldName, fieldInfo);
                }
            }
        }
        
        // 设置字段集合
        metadata.put("fields", fields);
        metadata.put("createdAt", new Date());
        metadata.put("updatedAt", new Date());
        
        return metadata;
    }
    
    // 所有FieldMetadata相关方法已被删除，现在使用简单的Map实现代替复杂的字段元数据结构
    
    // 使用后面的isValidFieldType方法实现

    /**
     * 注册模型
     */
    public void registerModel(Map<String, Object> entityMetadata) {
        Assert.notNull(entityMetadata, "实体元数据不能为空");
        
        try {
            // 从元数据中提取模型名称
            String modelName = getStringValue(entityMetadata, "apiName", null);
            if (modelName == null || modelName.trim().isEmpty()) {
                modelName = getStringValue(entityMetadata, "name", null);
            }
            
            Assert.hasText(modelName, "模型名称不能为空");
            
            // 验证模型是否已经存在
            if (modelExists(modelName)) {
                log.info("更新现有模型: {}", modelName);
            }
            
            // 深拷贝元数据以防止外部修改
            Map<String, Object> metadataCopy = deepCopyMap(entityMetadata);
            
            // 线程安全地注册模型
            modelsCache.put(modelName, metadataCopy);
            
            // 记录注册成功的日志
            Map<String, Object> fields = (Map<String, Object>) metadataCopy.get("fields");
            log.info("成功注册模型: {}, 字段数量: {}", 
                    modelName, 
                    fields != null ? fields.size() : 0);
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String modelName = entityMetadata != null ? getStringValue(entityMetadata, "apiName", "未知模型") : "未知模型";
            log.error("注册模型失败: {}", modelName, e);
            throw new BusinessException("注册模型失败: " + e.getMessage(), "MODEL_REGISTER_FAILED");
        }
    }

    /**
     * 获取所有已注册的模型
     */
    public Collection<Object> getAllRegisteredModels() {
        // 返回深拷贝以防止外部修改
        return modelsCache.values().stream()
                .map(model -> model instanceof Map ? deepCopyMap((Map<String, Object>) model) : model)
                .collect(Collectors.toList());
    }

    /**
     * 根据名称获取模型
     */
    public Object getModelByName(String modelName) {
        Assert.hasText(modelName, "模型名称不能为空");
        
        // 返回深拷贝以防止外部修改
        Object model = modelsCache.get(modelName);
        return model instanceof Map ? deepCopyMap((Map<String, Object>) model) : model;
    }
    
    /**
     * 检查模型是否存在
     */
    public boolean modelExists(String modelName) {
        Assert.hasText(modelName, "模型名称不能为空");
        return modelsCache.containsKey(modelName);
    }
    
    /**
     * 深拷贝Map对象，防止外部修改影响内部数据
     */
    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                // 递归复制嵌套的Map
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                copy.put(entry.getKey(), deepCopyMap(nestedMap));
            } else if (value instanceof List) {
                // 复制List
                @SuppressWarnings("unchecked")
                List<Object> originalList = (List<Object>) value;
                List<Object> copiedList = new ArrayList<>(originalList.size());
                for (Object item : originalList) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nestedMap = (Map<String, Object>) item;
                        copiedList.add(deepCopyMap(nestedMap));
                    } else {
                        copiedList.add(item); // 基本类型直接添加
                    }
                }
                copy.put(entry.getKey(), copiedList);
            } else {
                // 基本类型直接添加
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }
    
    /**
     * 复制模型（创建新模型）
     * @param sourceModelName 源模型名称
     * @param newModelName 新模型名称
     * @param newModelLabel 新模型标签
     * @return 创建的新模型元数据
     * @throws BusinessException 当模型不存在或创建失败时抛出
     */
    public Object duplicateModel(String sourceModelName, String newModelName, String newModelLabel) {
        Assert.hasText(sourceModelName, "源模型名称不能为空");
        Assert.hasText(newModelName, "新模型名称不能为空");
        
        try {
            // 检查源模型是否存在
            Object sourceMetadata = getModelByName(sourceModelName);
            if (sourceMetadata == null) {
                throw new BusinessException("源模型不存在: " + sourceModelName);
            }
            
            // 检查新模型名称是否已存在
            if (modelExists(newModelName)) {
                throw new BusinessException("新模型名称已存在: " + newModelName);
            }
            
            // 创建新模型定义，复制源模型的字段定义 - 简化实现
            Map<String, DynamicModelConfig.DynamicFieldDefinition> fields = new HashMap<>();
            
            // 从简化的模型数据中获取字段信息
            if (sourceMetadata instanceof Map<?, ?> sourceMap && sourceMap.get("fields") instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sourceFields = (Map<String, Object>) sourceMap.get("fields");
                
                for (Map.Entry<String, Object> entry : sourceFields.entrySet()) {
                    String fieldName = entry.getKey();
                    // 跳过ID字段，让createEntityMetadata方法自动创建
                    if (!"id".equals(fieldName)) {
                        Object fieldValue = entry.getValue();
                        if (fieldValue instanceof Map<?, ?>) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> fieldMap = (Map<String, Object>) fieldValue;
                            DynamicModelConfig.DynamicFieldDefinition newField = new DynamicModelConfig.DynamicFieldDefinition();
                            // 使用工具方法简化类型转换
                            newField.setType(getStringValue(fieldMap, "type", "string"));
                            newField.setLabel(getStringValue(fieldMap, "label", fieldName));
                            newField.setRequired(getBooleanValue(fieldMap, "required", false));
                            
                            // 设置默认值
                            Object defaultValueObj = fieldMap.get("defaultValue");
                            if (defaultValueObj != null) {
                                newField.setDefaultValue(defaultValueObj.toString());
                            }
                            
                            newField.setDescription(getStringValue(fieldMap, "description", null));
                            newField.setMaxLength(getIntegerValue(fieldMap, "maxLength"));
                            fields.put(fieldName, newField);
                        }
                    }
                }
            }
            
            // 创建新模型定义
            DynamicModelConfig.DynamicModelDefinition newDefinition = new DynamicModelConfig.DynamicModelDefinition();
            newDefinition.setLabel(newModelLabel != null ? newModelLabel : newModelName);
            
            // 从源模型获取描述
            if (sourceMetadata instanceof Map) {
                newDefinition.setDescription(getStringValue((Map<String, Object>) sourceMetadata, "description", null));
            }
            
            newDefinition.setFields(fields);
            
            // 创建并注册新模型
            Map<String, Object> newEntityMetadata = createEntityMetadata(newModelName, newDefinition);
            registerModel(newEntityMetadata);
            
            log.info("成功复制模型: {} -> {}, 字段数量: {}", 
                    sourceModelName, 
                    newModelName, 
                    fields.size());
            
            return newEntityMetadata;
        } catch (Exception e) {
            log.error("复制模型失败: {} -> {}", sourceModelName, newModelName, e);
            throw new BusinessException("复制模型失败: " + e.getMessage(), "MODEL_DUPLICATE_FAILED");
        }
    }

    /**
     * 创建新的动态模型
     */
    // 简化的创建模型实现
    public Object createModel(String modelName, String label, Map<String, DynamicModelConfig.DynamicFieldDefinition> fields) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(fields, "字段定义不能为空");
        
        try {
            // 检查模型是否已存在
            if (modelExists(modelName)) {
                throw new BusinessException("模型已存在: " + modelName);
            }
            
            // 创建模型定义
            DynamicModelConfig.DynamicModelDefinition definition = new DynamicModelConfig.DynamicModelDefinition();
            definition.setLabel(label != null ? label : modelName);
            definition.setFields(fields);
            
            // 创建实体元数据
            Map<String, Object> metadata = createEntityMetadata(modelName, definition);
            
            // 注册模型
            registerModel(metadata);
            
            // 更新配置中的模型定义
            dynamicModelConfig.getModels().put(modelName, definition);
            
            log.info("成功创建新动态模型: {}", modelName);
            return metadata;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建模型失败: {}", modelName, e);
            throw new BusinessException("创建模型失败: " + e.getMessage(), "MODEL_CREATE_FAILED");
        }
    }
    
    /**
     * 更新现有模型的字段
     */
    // 简化的更新模型字段实现
    public Object updateModelFields(String modelName, Map<String, DynamicModelConfig.DynamicFieldDefinition> updatedFields) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(updatedFields, "更新的字段定义不能为空");
        
        try {
            // 检查模型是否存在
            DynamicModelConfig.DynamicModelDefinition definition = dynamicModelConfig.getModels().get(modelName);
            if (definition == null) {
                throw new BusinessException("模型不存在: " + modelName);
            }
            
            // 获取现有字段并更新
            Map<String, DynamicModelConfig.DynamicFieldDefinition> currentFields = definition.getFields();
            if (currentFields == null) {
                currentFields = new HashMap<>();
                definition.setFields(currentFields);
            }
            
            // 更新或添加字段
            for (Map.Entry<String, DynamicModelConfig.DynamicFieldDefinition> fieldEntry : updatedFields.entrySet()) {
                String fieldName = fieldEntry.getKey();
                // 不允许修改ID字段
                if (!"id".equals(fieldName)) {
                    currentFields.put(fieldName, fieldEntry.getValue());
                }
            }
            
            // 重建并注册模型
            Map<String, Object> metadata = createEntityMetadata(modelName, definition);
            registerModel(metadata);
            
            log.info("成功更新动态模型字段: {}", modelName);
            return metadata;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新模型字段失败: {}", modelName, e);
            throw new BusinessException("更新模型字段失败: " + e.getMessage(), "MODEL_UPDATE_FAILED");
        }
    }
    
    /**
     * 从模型中删除字段
     */
    // 简化的删除模型字段实现
    public Object removeModelField(String modelName, String fieldName) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(fieldName, "字段名称不能为空");
        
        try {
            // 不允许删除ID字段
            if ("id".equals(fieldName)) {
                throw new BusinessException("不允许删除ID字段");
            }
            
            // 检查模型是否存在
            DynamicModelConfig.DynamicModelDefinition definition = dynamicModelConfig.getModels().get(modelName);
            if (definition == null) {
                throw new BusinessException("模型不存在: " + modelName);
            }
            
            // 移除字段
            if (definition.getFields() != null && definition.getFields().remove(fieldName) != null) {
                // 重建并注册模型
                Map<String, Object> metadata = createEntityMetadata(modelName, definition);
                registerModel(metadata);
                
                log.info("成功从模型中删除字段: {} - {}", modelName, fieldName);
                return metadata;
            } else {
                throw new BusinessException("字段不存在: " + fieldName);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除模型字段失败: {}, 字段名: {}", modelName, fieldName, e);
            throw new BusinessException("删除模型字段失败: " + e.getMessage(), "MODEL_FIELD_REMOVE_FAILED");
        }
    }

    /**
     * 删除动态模型
     */
    public void deleteModel(String modelName) {
        Assert.hasText(modelName, "模型名称不能为空");
        
        try {
            // 检查模型是否存在
            if (!modelExists(modelName)) {
                throw new BusinessException("模型不存在: " + modelName);
            }
            
            // 从模型缓存中删除
            modelsCache.remove(modelName);
            
            // 从配置中移除
            dynamicModelConfig.getModels().remove(modelName);
            
            log.info("动态模型已删除: {}", modelName);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除动态模型失败: {}", modelName, e);
            throw new BusinessException("删除动态模型失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量删除动态模型
     * @param modelNames 模型名称列表
     * @return 删除成功的数量
     */
    public int batchDeleteModels(List<String> modelNames) {
        // 参数校验
        Assert.notNull(modelNames, "模型名称列表不能为空");
        
        log.info("批量删除动态模型，数量: {}", modelNames.size());
        
        int successCount = 0;
        List<String> failedModels = new ArrayList<>();
        
        for (String modelName : modelNames) {
            try {
                deleteModel(modelName);
                successCount++;
            } catch (Exception e) {
                failedModels.add(modelName);
                log.warn("删除模型失败: {}", modelName, e);
            }
        }
        
        if (!failedModels.isEmpty()) {
            log.warn("部分模型删除失败，失败数量: {}, 失败模型: {}", 
                     failedModels.size(), String.join(", ", failedModels));
        }
        
        log.info("批量删除动态模型完成，成功数量: {}, 失败数量: {}", 
                 successCount, failedModels.size());
        return successCount;
    }
    
    /**
     * 重新加载指定模型
     */
    // 简化的重新加载模型实现
    public Object reloadModel(String modelName) {
        Assert.hasText(modelName, "模型名称不能为空");
        
        try {
            // 获取现有模型定义
            DynamicModelConfig.DynamicModelDefinition definition = dynamicModelConfig.getModels().get(modelName);
            if (definition == null) {
                log.warn("模型不存在，无法重新加载: {}", modelName);
                throw new BusinessException("模型不存在: " + modelName);
            }
            
            // 重建实体元数据
            Map<String, Object> metadata = createEntityMetadata(modelName, definition);
            
            // 重新注册模型
            registerModel(metadata);
            
            log.info("成功重新加载模型: {}", modelName);
            
            return metadata;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("重新加载模型失败: {}", modelName, e);
            throw new BusinessException("重新加载模型失败: " + e.getMessage(), "MODEL_RELOAD_FAILED");
        }
    }
    
    /**
     * 批量重新加载动态模型
     * @param modelNames 模型名称列表
     * @return 重新加载成功的数量
     */
    public int batchReloadModels(List<String> modelNames) {
        // 参数校验
        Assert.notNull(modelNames, "模型名称列表不能为空");
        
        log.info("批量重新加载动态模型，数量: {}", modelNames.size());
        
        int successCount = 0;
        List<String> failedModels = new ArrayList<>();
        
        for (String modelName : modelNames) {
            try {
                reloadModel(modelName);
                successCount++;
            } catch (Exception e) {
                failedModels.add(modelName);
                log.warn("重新加载模型失败: {}", modelName, e);
            }
        }
        
        if (!failedModels.isEmpty()) {
            log.warn("部分模型重新加载失败，失败数量: {}, 失败模型: {}", 
                     failedModels.size(), String.join(", ", failedModels));
        }
        
        log.info("批量重新加载动态模型完成，成功数量: {}, 失败数量: {}", 
                 successCount, failedModels.size());
        return successCount;
    }
    
    /**
     * 刷新所有动态模型
     */
    public void refreshAllModels() {
        try {
            // 重新加载所有已配置的模型
            modelsCache.clear();
            loadAllDynamicModels();
            log.info("所有动态模型已刷新");
        } catch (Exception e) {
            log.error("刷新动态模型失败", e);
            throw new BusinessException("刷新动态模型失败: " + e.getMessage(), "MODEL_REFRESH_FAILED");
        }
    }
    
    public Map<String, Object> getModelFields(String modelName) {
        Assert.hasText(modelName, "模型名称不能为空");
        
        Object modelObj = getModelByName(modelName);
        if (modelObj == null) {
            log.error("尝试获取不存在的模型字段: {}", modelName);
            return Collections.emptyMap();
        }
        
        if (!(modelObj instanceof Map<?, ?> modelMap)) {
            log.error("模型数据格式错误: {}", modelName);
            return Collections.emptyMap();
        }
        
        Object fieldsObj = modelMap.get("fields");
        if (!(fieldsObj instanceof Map<?, ?> fieldsMap)) {
            log.error("模型字段数据格式错误: {}", modelName);
            return Collections.emptyMap();
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> typedFields = (Map<String, Object>) fieldsMap;
        return typedFields;
    }
    
    /**
     * 获取模型的单个字段元数据
     */
    public Object getModelField(String modelName, String fieldName) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(fieldName, "字段名称不能为空");
        
        // 获取模型字段映射
        Map<String, Object> fields = getModelFields(modelName);
        if (fields.isEmpty()) {
            throw new BusinessException("字段不存在: " + fieldName);
        }
        
        Object field = fields.get(fieldName);
        if (field == null) {
            throw new BusinessException("字段不存在: " + fieldName);
        }
        
        return field;
    }
    
    /**
     * 验证字段类型是否合法
     * @param fieldType 字段类型
     * @return 是否合法
     */
    private boolean isValidFieldType(String fieldType) {
        // 支持的字段类型列表
        Set<String> validTypes = new HashSet<>(Arrays.asList(
            "string", "number", "integer", "boolean", "date", "datetime", 
            "text", "decimal", "long", "short", "float", "double",
            "enum", "reference", "array", "object", "file", "binary"
        ));
        return validTypes.contains(fieldType.toLowerCase());
    }
    
    /**
     * 安全地从Map中获取String类型值
     */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }
    
    /**
     * 安全地从Map中获取Boolean类型值
     */
    private Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }
    
    /**
     * 安全地从Map中获取Integer类型值
     */
    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log.debug("Invalid integer value for key {}: {}", key, value);
            }
        }
        return null;
    }
    
    /**
     * 验证模型定义是否合法
     * @param modelName 模型名称
     * @param fields 字段定义
     * @return 验证结果，如果验证通过则返回null，否则返回错误信息
     */
    public String validateModelDefinition(String modelName, Map<String, DynamicModelConfig.DynamicFieldDefinition> fields) {
        try {
            // 验证模型名称
            if (modelName == null || modelName.trim().isEmpty()) {
                return "模型名称不能为空";
            }
            
            // 验证模型名称格式
            if (!modelName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                return "模型名称只能包含字母、数字和下划线，且必须以字母或下划线开头";
            }
            
            // 验证字段
            if (fields == null || fields.isEmpty()) {
                return "模型必须包含至少一个字段";
            }
            
            for (Map.Entry<String, DynamicModelConfig.DynamicFieldDefinition> entry : fields.entrySet()) {
                String fieldName = entry.getKey();
                DynamicModelConfig.DynamicFieldDefinition field = entry.getValue();
                
                // 验证字段名称
                if (fieldName == null || fieldName.trim().isEmpty()) {
                    return "字段名称不能为空";
                }
                
                // 验证字段名称格式
                if (!fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                    return "字段名称只能包含字母、数字和下划线，且必须以字母或下划线开头: " + fieldName;
                }
                
                // 验证字段标签
                if (field.getLabel() == null || field.getLabel().trim().isEmpty()) {
                    return "字段标签不能为空: " + fieldName;
                }
                
                // 验证字段类型
                if (field.getType() == null || field.getType().trim().isEmpty()) {
                    return "字段类型不能为空: " + fieldName;
                }
                
                // 验证字段类型是否合法
                if (!isValidFieldType(field.getType())) {
                    return "无效的字段类型: " + field.getType() + " for field " + fieldName;
                }
                
                // 验证字段长度约束
                if (field.getMaxLength() != null && field.getMaxLength() <= 0) {
                    return "字段最大长度必须大于0: " + fieldName;
                }
                
                if (field.getMinLength() != null && field.getMinLength() < 0) {
                    return "字段最小长度不能小于0: " + fieldName;
                }
                
                if (field.getMaxLength() != null && field.getMinLength() != null && 
                    field.getMaxLength() < field.getMinLength()) {
                    return "字段最大长度不能小于最小长度: " + fieldName;
                }
            }
            
            // 所有验证通过
            return null;
        } catch (Exception e) {
            log.error("验证模型定义失败", e);
            return "验证模型定义失败: " + e.getMessage();
        }
    }
    
    /**
     * 获取模型信息统计
     * @return 模型信息统计
     */
    public Map<String, Object> getModelStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 简化实现：遍历缓存中的模型信息
            if (modelsCache != null) {
                int modelCount = modelsCache.size();
                int totalFields = 0;
                
                for (Object modelInfo : modelsCache.values()) {
                    if (modelInfo instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> modelMetadata = (Map<String, Object>) modelInfo;
                        Object fieldsObj = modelMetadata.get("fields");
                        if (fieldsObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> fields = (Map<String, Object>) fieldsObj;
                            totalFields += fields.size();
                        }
                    }
                }
                
                stats.put("modelCount", modelCount);
                stats.put("totalFields", totalFields);
                stats.put("avgFieldsPerModel", modelCount > 0 ? (double) totalFields / modelCount : 0.0);
                
                log.info("模型统计: 模型数量={}, 字段总数={}", modelCount, totalFields);
            }
        } catch (Exception e) {
            log.error("获取模型统计信息失败", e);
        }
        
        return stats;
    }
}