package com.bone.procurement.service;

import com.bone.procurement.config.DynamicModelConfig;
import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.FieldMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 动态模型管理器
 * 负责动态模型的加载、注册和管理
 */
@Service
public class DynamicModelManager implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DynamicModelManager.class);

    @Autowired
    private DynamicModelConfig dynamicModelConfig;

    @Autowired
    private MetadataEngine metadataEngine;

    // 已注册的动态模型缓存
    private final Map<String, EntityMetadata> registeredModels = new HashMap<>();

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
     * 创建实体元数据
     */
    private EntityMetadata createEntityMetadata(String modelName, 
                                              DynamicModelConfig.DynamicModelDefinition definition) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setApiName(modelName);
        metadata.setLabel(definition.getLabel() != null ? definition.getLabel() : modelName);
        metadata.setDescription(definition.getDescription());
        
        // 添加ID字段作为主键
        FieldMetadata idField = createIdField();
        
        // 转换配置中的字段定义为元数据字段
        Map<String, FieldMetadata> fields = new HashMap<>();
        fields.put("id", idField);
        
        for (Map.Entry<String, DynamicModelConfig.DynamicFieldDefinition> fieldEntry : definition.getFields().entrySet()) {
            String fieldName = fieldEntry.getKey();
            DynamicModelConfig.DynamicFieldDefinition fieldDef = fieldEntry.getValue();
            
            // 避免覆盖ID字段
            if (!"id".equals(fieldName)) {
                FieldMetadata fieldMetadata = createFieldMetadata(fieldName, fieldDef);
                fields.put(fieldName, fieldMetadata);
            }
        }
        
        metadata.setFields(fields);
        return metadata;
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
        return idField;
    }

    /**
     * 创建字段元数据
     */
    private FieldMetadata createFieldMetadata(String fieldName, 
                                           DynamicModelConfig.DynamicFieldDefinition fieldDef) {
        FieldMetadata field = new FieldMetadata();
        field.setApiName(fieldName);
        field.setLabel(fieldDef.getLabel() != null ? fieldDef.getLabel() : fieldName);
        field.setType(fieldDef.getType());
        field.setRequired(fieldDef.isRequired());
        field.setDescription(fieldDef.getDescription());
        
        // 设置字段约束
        field.setMaxLength(fieldDef.getMaxLength());
        field.setDefaultValue(fieldDef.getDefaultValue());
        
        // 存储额外的字段约束信息
        Map<String, Object> attributes = new HashMap<>();
        if (fieldDef.getMinLength() != null) {
            attributes.put("minLength", fieldDef.getMinLength());
        }
        if (fieldDef.getMinValue() != null) {
            attributes.put("minValue", fieldDef.getMinValue());
        }
        if (fieldDef.getMaxValue() != null) {
            attributes.put("maxValue", fieldDef.getMaxValue());
        }
        if (fieldDef.getPattern() != null) {
            attributes.put("pattern", fieldDef.getPattern());
        }
        
        if (!attributes.isEmpty()) {
            field.setAttributes(attributes);
        }
        
        return field;
    }

    /**
     * 注册动态模型到元数据引擎
     */
    public void registerModel(EntityMetadata entityMetadata) {
        try {
            // 检查模型是否已存在
            if (registeredModels.containsKey(entityMetadata.getApiName())) {
                // 更新已存在的模型
                metadataEngine.updateEntity(entityMetadata);
            } else {
                // 注册新模型
                metadataEngine.registerEntity(entityMetadata);
            }
            
            // 更新本地缓存
            registeredModels.put(entityMetadata.getApiName(), entityMetadata);
        } catch (Exception e) {
            log.error("注册动态模型失败: {}", entityMetadata.getApiName(), e);
            throw e;
        }
    }

    /**
     * 获取所有已注册的动态模型
     */
    public Collection<EntityMetadata> getAllRegisteredModels() {
        return new ArrayList<>(registeredModels.values());
    }

    /**
     * 根据名称获取动态模型
     */
    public EntityMetadata getModelByName(String modelName) {
        return registeredModels.get(modelName);
    }

    /**
     * 创建新的动态模型
     */
    public EntityMetadata createModel(String modelName, String label, Map<String, DynamicModelConfig.DynamicFieldDefinition> fields) {
        DynamicModelConfig.DynamicModelDefinition definition = new DynamicModelConfig.DynamicModelDefinition();
        definition.setLabel(label);
        definition.setFields(fields);
        
        EntityMetadata metadata = createEntityMetadata(modelName, definition);
        registerModel(metadata);
        
        // 更新配置中的模型定义
        dynamicModelConfig.getModels().put(modelName, definition);
        
        return metadata;
    }

    /**
     * 删除动态模型
     */
    public void deleteModel(String modelName) {
        if (registeredModels.containsKey(modelName)) {
            // 从本地缓存移除
            registeredModels.remove(modelName);
            // 从配置中移除
            dynamicModelConfig.getModels().remove(modelName);
            
            // TODO: 这里可以添加从元数据引擎中删除模型的逻辑
            // 注意：删除模型需要谨慎，因为可能会影响已有数据
            log.info("动态模型已从配置中移除: {}", modelName);
        }
    }
}