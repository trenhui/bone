package com.bone.procurement.service;

import com.bone.procurement.config.DynamicModelConfig;
import com.bone.procurement.exception.BusinessException;
import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
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

    @Autowired
    private MetadataEngine metadataEngine;

    /**
     * 初始化方法，在应用启动时加载所有动态模型
     */
    @Override
    public void afterPropertiesSet() {
        if (dynamicModelConfig.isEnabled()) {
            loadAllDynamicModels();
        }
    }

    /**
     * 加载所有配置的动态模型
     */
    public void loadAllDynamicModels() {
        if (dynamicModelConfig.getModels().isEmpty()) {
            log.info("未找到配置的动态模型");
            return;
        }

        log.info("开始加载动态模型，共 {} 个模型", dynamicModelConfig.getModels().size());

        for (Map.Entry<String, DynamicModelConfig.DynamicModelDefinition> entry : dynamicModelConfig.getModels().entrySet()) {
            String modelName = entry.getKey();
            DynamicModelConfig.DynamicModelDefinition definition = entry.getValue();
            
            try {
                EntityMetadata entityMetadata = createEntityMetadata(modelName, definition);
                registerModel(entityMetadata);
                log.info("成功加载动态模型: {}", modelName);
            } catch (Exception e) {
                log.error("加载动态模型失败: {}", modelName, e);
            }
        }
    }

    /**
     * 创建实体元数据 - 支持四阶驱动模型的标准字段
     */
    private EntityMetadata createEntityMetadata(String modelName, 
                                              DynamicModelConfig.DynamicModelDefinition definition) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(definition, "模型定义不能为空");
        
        EntityMetadata metadata = new EntityMetadata();
        metadata.setApiName(modelName);
        metadata.setLabel(definition.getLabel() != null ? definition.getLabel() : modelName);
        metadata.setDescription(definition.getDescription());
        
        // 设置业务域为procurement，便于分类管理
        metadata.setDomain("procurement");
        
        // 添加ID字段作为主键
        FieldMetadata idField = createIdField();
        
        // 使用LinkedHashMap保持字段顺序
        Map<String, FieldMetadata> fields = new LinkedHashMap<>();
        fields.put("id", idField);
        
        // 添加标准版本管理字段 - 支持四阶驱动模型的并发控制
        fields.put("version", createVersionField());
        
        // 添加标准审计字段 - 支持四阶驱动模型的追踪和审计
        fields.put("createdAt", createAuditField("createdAt", "创建时间", "datetime"));
        fields.put("createdBy", createAuditField("createdBy", "创建者", "string"));
        fields.put("updatedAt", createAuditField("updatedAt", "更新时间", "datetime"));
        fields.put("updatedBy", createAuditField("updatedBy", "更新者", "string"));
        
        // 添加多租户字段 - 支持多租户隔离
        fields.put("tenantId", createTenantField());
        
        // 转换配置中的字段定义为元数据字段
        if (definition.getFields() != null) {
            for (Map.Entry<String, DynamicModelConfig.DynamicFieldDefinition> fieldEntry : definition.getFields().entrySet()) {
                String fieldName = fieldEntry.getKey();
                DynamicModelConfig.DynamicFieldDefinition fieldDef = fieldEntry.getValue();
                
                // 避免覆盖系统保留字段
                if (!fields.containsKey(fieldName)) {
                    FieldMetadata fieldMetadata = createFieldMetadata(fieldName, fieldDef);
                    fields.put(fieldName, fieldMetadata);
                } else {
                    log.warn("字段 {} 是系统保留字段，将使用系统定义", fieldName);
                }
            }
        }
        
        metadata.setFields(fields);
        
        // 设置模型配置 - 支持四阶驱动模型的高级特性
        metadata.setSupportsVersioning(true);
        metadata.setSupportsAuditing(true);
        metadata.setMultiTenant(true);
        
        return metadata;
    }
    
    /**
     * 创建版本字段
     */
    private FieldMetadata createVersionField() {
        FieldMetadata versionField = new FieldMetadata();
        versionField.setApiName("version");
        versionField.setLabel("版本号");
        versionField.setType("long");
        versionField.setDefaultValue(0L);
        versionField.setRequired(true);
        versionField.setReadOnly(true);
        return versionField;
    }
    
    /**
     * 创建审计字段
     */
    private FieldMetadata createAuditField(String apiName, String label, String type) {
        FieldMetadata field = new FieldMetadata();
        field.setApiName(apiName);
        field.setLabel(label);
        field.setType(type);
        field.setReadOnly(true);
        return field;
    }
    
    /**
     * 创建租户字段
     */
    private FieldMetadata createTenantField() {
        FieldMetadata tenantField = new FieldMetadata();
        tenantField.setApiName("tenantId");
        tenantField.setLabel("租户ID");
        tenantField.setType("string");
        tenantField.setRequired(true);
        return tenantField;
    }

    /**
     * 创建ID字段
     */
    private FieldMetadata createIdField() {
        FieldMetadata idField = new FieldMetadata();
        idField.setApiName("id");
        idField.setLabel("ID");
        idField.setType("string");
        idField.setRequired(true);
        // 直接设置主键属性
        idField.setPrimaryKey(true);
        return idField;
    }

    /**
     * 创建字段元数据
     */
    private FieldMetadata createFieldMetadata(String fieldName, 
                                           DynamicModelConfig.DynamicFieldDefinition fieldDef) {
        Assert.hasText(fieldName, "字段名称不能为空");
        Assert.notNull(fieldDef, "字段定义不能为空");
        
        FieldMetadata field = new FieldMetadata();
        field.setApiName(fieldName);
        field.setLabel(fieldDef.getLabel() != null ? fieldDef.getLabel() : fieldName);
        
        // 标准化类型设置，确保类型有效
        String fieldType = fieldDef.getType() != null ? fieldDef.getType() : "string";
        // 验证字段类型
        if (!isValidFieldType(fieldType)) {
            log.warn("未知字段类型 '{}'，使用默认类型 'string'", fieldType);
            fieldType = "string";
        }
        field.setType(fieldType);
        
        field.setRequired(fieldDef.isRequired());
        field.setDescription(fieldDef.getDescription());
        
        // 设置字段约束
        if (fieldDef.getMaxLength() != null) {
            field.setLength(fieldDef.getMaxLength());
        }
        field.setDefaultValue(fieldDef.getDefaultValue());
        
        // 添加额外的字段约束
        if (fieldDef.getMinLength() != null) {
            field.setMinLength(fieldDef.getMinLength());
        }
        if (fieldDef.getMinValue() != null) {
            field.setMinValue(fieldDef.getMinValue());
        }
        if (fieldDef.getMaxValue() != null) {
            field.setMaxValue(fieldDef.getMaxValue());
        }
        if (fieldDef.getPattern() != null) {
            field.setPattern(fieldDef.getPattern());
        }
        
        return field;
    }
    
    /**
     * 验证字段类型是否有效
     */
    private boolean isValidFieldType(String type) {
        Set<String> validTypes = new HashSet<>(Arrays.asList(
            "string", "integer", "long", "double", "boolean", "date", "datetime", "object", "array"
        ));
        return validTypes.contains(type.toLowerCase());
    }

    /**
     * 注册动态模型到元数据引擎 - 支持四阶驱动模型的版本管理和影响分析
     */
    public void registerModel(EntityMetadata entityMetadata) {
        Assert.notNull(entityMetadata, "实体元数据不能为空");
        Assert.hasText(entityMetadata.getApiName(), "实体API名称不能为空");
        
        try {
            // 设置创建时间和创建者信息
            if (entityMetadata.getCreatedAt() == null) {
                entityMetadata.setCreatedAt(new Date());
                entityMetadata.setCreatedBy("system"); // 在实际实现中应使用当前用户
            }
            
            // 设置模型版本
            if (entityMetadata.getVersion() == null) {
                entityMetadata.setVersion(1L);
            }
            
            // 获取现有实体元数据进行比较，支持模型演化
            EntityMetadata existingMetadata = metadataEngine.getEntityMetadata(entityMetadata.getApiName());
            if (existingMetadata != null) {
                // 递增版本号
                entityMetadata.setVersion(existingMetadata.getVersion() + 1);
                entityMetadata.setUpdatedAt(new Date());
                entityMetadata.setUpdatedBy("system"); // 在实际实现中应使用当前用户
                
                // 更新已存在的模型
                metadataEngine.registerEntityMetadata(entityMetadata, true); // 使用true表示更新
                log.info("更新动态模型: {}, 版本: {}", 
                        entityMetadata.getApiName(), 
                        entityMetadata.getVersion());
            } else {
                // 注册新模型
                metadataEngine.registerEntityMetadata(entityMetadata, false); // 使用false表示新建
                log.info("注册新动态模型: {}, 版本: {}", 
                        entityMetadata.getApiName(), 
                        entityMetadata.getVersion());
            }
        } catch (Exception e) {
            log.error("注册动态模型失败: {}", entityMetadata.getApiName(), e);
            throw new BusinessException("注册动态模型失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有已注册的动态模型
     */
    public Collection<EntityMetadata> getAllRegisteredModels() {
        try {
            List<EntityMetadata> allEntities = metadataEngine.getAllEntityMetadata();
            return allEntities.stream()
                    .filter(entity -> "procurement".equals(entity.getDomain()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取所有动态模型失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据名称获取动态模型
     */
    public EntityMetadata getModelByName(String modelName) {
        try {
            return metadataEngine.getEntityMetadata(modelName);
        } catch (Exception e) {
            log.error("获取动态模型失败: {}", modelName, e);
            return null;
        }
    }
    
    /**
     * 检查模型是否存在
     */
    public boolean modelExists(String modelName) {
        try {
            return getModelByName(modelName) != null;
        } catch (Exception e) {
            log.error("检查模型是否存在失败: {}", modelName, e);
            return false;
        }
    }
    
    /**
     * 复制模型（创建新模型）
     * @param sourceModelName 源模型名称
     * @param newModelName 新模型名称
     * @param newModelLabel 新模型标签
     * @return 创建的新模型元数据
     * @throws BusinessException 当模型不存在或创建失败时抛出
     */
    public EntityMetadata duplicateModel(String sourceModelName, String newModelName, String newModelLabel) {
        Assert.hasText(sourceModelName, "源模型名称不能为空");
        Assert.hasText(newModelName, "新模型名称不能为空");
        
        try {
            // 检查源模型是否存在
            EntityMetadata sourceMetadata = getModelByName(sourceModelName);
            if (sourceMetadata == null) {
                throw new BusinessException("源模型不存在: " + sourceModelName);
            }
            
            // 检查新模型名称是否已存在
            if (modelExists(newModelName)) {
                throw new BusinessException("新模型名称已存在: " + newModelName);
            }
            
            // 创建新模型定义，复制源模型的字段定义
            Map<String, DynamicModelConfig.DynamicFieldDefinition> fields = new HashMap<>();
            for (Map.Entry<String, FieldMetadata> entry : sourceMetadata.getFields().entrySet()) {
                // 跳过ID字段，让createEntityMetadata方法自动创建
                if (!"id".equals(entry.getKey())) {
                    FieldMetadata sourceField = entry.getValue();
                    DynamicModelConfig.DynamicFieldDefinition newField = new DynamicModelConfig.DynamicFieldDefinition();
                    newField.setType(sourceField.getType());
                    newField.setLabel(sourceField.getLabel());
                    newField.setRequired(sourceField.isRequired());
                    newField.setDefaultValue(sourceField.getDefaultValue());
                    newField.setDescription(sourceField.getDescription());
                    
                    // 复制其他约束
                    if (sourceField.getLength() != null) {
                        newField.setMaxLength(sourceField.getLength());
                    }
                    if (sourceField.getMinLength() != null) {
                        newField.setMinLength(sourceField.getMinLength());
                    }
                    if (sourceField.getMinValue() != null) {
                        newField.setMinValue(sourceField.getMinValue());
                    }
                    if (sourceField.getMaxValue() != null) {
                        newField.setMaxValue(sourceField.getMaxValue());
                    }
                    if (sourceField.getPattern() != null) {
                        newField.setPattern(sourceField.getPattern());
                    }
                    
                    fields.put(sourceField.getApiName(), newField);
                }
            }
            
            // 创建新模型
            return createModel(newModelName, newModelLabel, fields);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("复制模型失败: 从 {} 到 {}", sourceModelName, newModelName, e);
            throw new BusinessException("复制模型失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建新的动态模型
     */
    public EntityMetadata createModel(String modelName, String label, Map<String, DynamicModelConfig.DynamicFieldDefinition> fields) {
        Assert.hasText(modelName, "模型名称不能为空");
        
        // 检查模型是否已存在
        if (metadataEngine.getEntityMetadata(modelName) != null) {
            throw new BusinessException("模型已存在: " + modelName);
        }
        
        DynamicModelConfig.DynamicModelDefinition definition = new DynamicModelConfig.DynamicModelDefinition();
        definition.setLabel(label != null ? label : modelName);
        definition.setFields(fields != null ? fields : new HashMap<>());
        
        EntityMetadata metadata = createEntityMetadata(modelName, definition);
        registerModel(metadata);
        
        // 更新配置中的模型定义
        dynamicModelConfig.getModels().put(modelName, definition);
        
        log.info("成功创建新动态模型: {}", modelName);
        return metadata;
    }
    
    /**
     * 更新现有模型的字段
     */
    public EntityMetadata updateModelFields(String modelName, Map<String, DynamicModelConfig.DynamicFieldDefinition> updatedFields) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.notNull(updatedFields, "更新的字段不能为空");
        
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
        EntityMetadata metadata = createEntityMetadata(modelName, definition);
        registerModel(metadata);
        
        log.info("成功更新动态模型字段: {}", modelName);
        return metadata;
    }
    
    /**
     * 从模型中删除字段
     */
    public EntityMetadata removeModelField(String modelName, String fieldName) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(fieldName, "字段名称不能为空");
        
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
            EntityMetadata metadata = createEntityMetadata(modelName, definition);
            registerModel(metadata);
            
            log.info("成功从模型中删除字段: {} - {}", modelName, fieldName);
            return metadata;
        } else {
            throw new BusinessException("字段不存在: " + fieldName);
        }
    }

    /**
     * 删除动态模型
     */
    public void deleteModel(String modelName) {
        Assert.hasText(modelName, "模型名称不能为空");
        
        try {
            // 检查模型是否存在
            if (metadataEngine.getEntityMetadata(modelName) == null) {
                throw new BusinessException("模型不存在: " + modelName);
            }
            
            // 从元数据引擎中删除模型
            metadataEngine.unregisterEntity(modelName);
            
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
    public EntityMetadata reloadModel(String modelName) {
        Assert.hasText(modelName, "模型名称不能为空");
        
        if (dynamicModelConfig.getModels().containsKey(modelName)) {
            DynamicModelConfig.DynamicModelDefinition definition = dynamicModelConfig.getModels().get(modelName);
            EntityMetadata metadata = createEntityMetadata(modelName, definition);
            registerModel(metadata);
            log.info("重新加载动态模型: {}", modelName);
            return metadata;
        } else {
            log.warn("模型不存在，无法重新加载: {}", modelName);
            throw new BusinessException("模型不存在: " + modelName);
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
            metadataEngine.refreshMetadata();
            log.info("所有动态模型已刷新");
        } catch (Exception e) {
            log.error("刷新动态模型失败", e);
            throw new BusinessException("刷新动态模型失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取模型的字段元数据
     */
    public Map<String, FieldMetadata> getModelFields(String modelName) {
        Assert.hasText(modelName, "模型名称不能为空");
        
        EntityMetadata metadata = getModelByName(modelName);
        if (metadata == null) {
            throw new BusinessException("模型不存在: " + modelName);
        }
        
        return metadata.getFields() != null ? metadata.getFields() : Collections.emptyMap();
    }
    
    /**
     * 获取模型的单个字段元数据
     */
    public FieldMetadata getModelField(String modelName, String fieldName) {
        Assert.hasText(modelName, "模型名称不能为空");
        Assert.hasText(fieldName, "字段名称不能为空");
        
        Map<String, FieldMetadata> fields = getModelFields(modelName);
        FieldMetadata field = fields.get(fieldName);
        
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
            // 获取所有实体元数据
            List<EntityMetadata> allMetadata = metadataEngine.getAllEntityMetadata();
            int totalModels = allMetadata.size();
            
            // 统计字段数量
            int totalFields = 0;
            int totalRequiredFields = 0;
            Map<String, Integer> fieldTypeCount = new HashMap<>();
            
            for (EntityMetadata metadata : allMetadata) {
                if (metadata.getFields() != null) {
                    for (FieldMetadata field : metadata.getFields().values()) {
                        totalFields++;
                        if (field.isRequired()) {
                            totalRequiredFields++;
                        }
                        String fieldTypeName = field.getType();
                        fieldTypeCount.put(fieldTypeName, fieldTypeCount.getOrDefault(fieldTypeName, 0) + 1);
                    }
                }
            }
            
            // 添加更多统计信息
            int procurementModels = 0;
            for (EntityMetadata metadata : allMetadata) {
                if ("procurement".equals(metadata.getDomain())) {
                    procurementModels++;
                }
            }
            
            stats.put("totalModels", totalModels);
            stats.put("procurementModels", procurementModels);
            stats.put("totalFields", totalFields);
            stats.put("totalRequiredFields", totalRequiredFields);
            stats.put("fieldTypeDistribution", fieldTypeCount);
            
            // 添加平均字段数
            if (totalModels > 0) {
                stats.put("avgFieldsPerModel", (double) totalFields / totalModels);
            } else {
                stats.put("avgFieldsPerModel", 0.0);
            }
            
            return stats;
        } catch (Exception e) {
            log.error("获取模型信息统计失败", e);
            throw new BusinessException("获取模型信息统计失败: " + e.getMessage(), e);
        }
    }
}