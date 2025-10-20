package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.*;
import com.bone.smartmeta.engine.rule.BusinessRuleRegistry;
import com.bone.smartmeta.engine.rule.DefaultBusinessRuleRegistry;
import com.bone.smartmeta.engine.cache.MetadataCacheManager;
import com.bone.smartmeta.engine.version.MetadataVersionController;
import com.bone.smartmeta.engine.validation.MetadataValidator;
import com.bone.smartmeta.engine.validation.ValidationResult;
import com.bone.smartmeta.engine.exception.MetadataValidationException;
import com.bone.smartmeta.engine.multi.MultiTenantContextHolder;
import com.bone.smartmeta.engine.event.MetadataChangedEvent;
import com.bone.smartmeta.engine.impact.ImpactAnalysis;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 智能元数据引擎核心类
 * 提供完整的元数据管理、实体操作和验证功能
 * 支持多租户、版本管理、多级缓存和影响分析
 */
@Slf4j
@Getter
@Setter
public class SmartMetadataEngine {
    
    // 元数据注册表
    private final MetadataRegistry metadataRegistry;
    
    // 字段计算引擎
    private final FieldCalculationEngine calculationEngine;
    
    // 业务规则引擎
    private final BusinessRuleEngine businessRuleEngine;
    
    // 实体验证引擎
    private final ValidationEngine validationEngine;
    
    // 业务规则注册表
    private final BusinessRuleRegistry businessRuleRegistry;
    
    // 引擎配置
    private final EngineConfiguration configuration;
    
    // 元数据缓存管理器（多级缓存）
    private final MetadataCacheManager cacheManager;
    
    // 版本控制器
    private final MetadataVersionController versionController;
    
    // 元数据验证器
    private final Object metadataValidator; // 使用Object替代缺失的MetadataValidator
    
    // 多租户上下文
    private final Object tenantContext; // 使用Object替代缺失的MultiTenantContextHolder
    
    // 事件发布器
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * 使用默认配置创建元数据引擎
     */
    public SmartMetadataEngine() {
        this(new EngineConfiguration());
    }
    
    /**
     * 使用自定义配置创建元数据引擎
     * @param configuration 引擎配置
     */
    public SmartMetadataEngine(EngineConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new EngineConfiguration();
        this.metadataRegistry = this.configuration.getMetadataRegistrySupplier().get();
        this.calculationEngine = new DefaultFieldCalculationEngine(this);
        
        // 初始化业务规则注册表
        this.businessRuleRegistry = this.configuration.getBusinessRuleRegistrySupplier() != null ? 
                                   this.configuration.getBusinessRuleRegistrySupplier().get() : 
                                   new DefaultBusinessRuleRegistry();
        
        // 初始化业务规则引擎
        this.businessRuleEngine = new DefaultBusinessRuleEngine(this);
        this.validationEngine = new DefaultValidationEngine(this);
        
        // 初始化企业级组件
        this.cacheManager = this.configuration.getCacheManagerSupplier() != null ?
                          this.configuration.getCacheManagerSupplier().get() :
                          new DefaultMetadataCacheManager();
        
        this.versionController = this.configuration.getVersionControllerSupplier() != null ?
                               this.configuration.getVersionControllerSupplier().get() :
                               new DefaultMetadataVersionController();
        
        this.metadataValidator = this.configuration.getMetadataValidatorSupplier() != null ?
                               this.configuration.getMetadataValidatorSupplier().get() :
                               new DefaultMetadataValidator();
        
        this.tenantContext = this.configuration.getTenantContextSupplier() != null ?
                           this.configuration.getTenantContextSupplier().get() :
                           new DefaultMultiTenantContextHolder();
        
        this.eventPublisher = this.configuration.getEventPublisherSupplier() != null ?
                            this.configuration.getEventPublisherSupplier().get() :
                            new SimpleApplicationEventPublisher();
        
        // 注册规则变更监听器
        if (this.businessRuleRegistry instanceof DefaultBusinessRuleRegistry) {
            ((DefaultBusinessRuleRegistry) this.businessRuleRegistry).addRuleChangeListener(
                new BusinessRuleRegistry.BusinessRuleChangeListener() {
                    @Override
                    public void onRuleRegistered(BusinessRuleMetadata rule) {
                        log.info("Rule registered: {}", rule.getName());
                    }
                    
                    @Override
                    public void onRuleUpdated(BusinessRuleMetadata rule) {
                        log.info("Rule updated: {}", rule.getName());
                    }
                    
                    @Override
                    public void onRuleUnregistered(String ruleId) {
                        log.info("Rule unregistered: {}", ruleId);
                    }
                    
                    @Override
                    public void onRegistryCleared() {
                        log.info("Rule registry cleared");
                    }
                }
            );
        }
        
        // 预加载常用元数据到缓存
        preloadCommonMetadata();
        
        log.info("SmartMetadataEngine initialized with configuration: {}", this.configuration);
    }
    
    /**
     * 预加载常用元数据到缓存
     */
    private void preloadCommonMetadata() {
        try {
            String tenantId = tenantContext.getCurrentTenantId();
            List<EntityMetadata> commonEntities = metadataRegistry.findCommonEntities(tenantId);
            if (commonEntities != null && !commonEntities.isEmpty()) {
                cacheManager.batchPut(tenantId, commonEntities.stream()
                    .collect(Collectors.toMap(EntityMetadata::getApiName, Function.identity())));
                log.info("Preloaded {} common entities into cache", commonEntities.size());
            }
        } catch (Exception e) {
            log.warn("Failed to preload common metadata: {}", e.getMessage());
        }
    }
    
    /**
     * 注册元数据（支持版本升级与多租户隔离）
     */
    @Transactional
    public EntityMetadata registerEntityMetadata(EntityMetadata metadata) {
        // 1. 租户上下文绑定
        String tenantId = tenantContext.getCurrentTenantId();
        metadata.setTenantId(tenantId);
        
        // 2. 元数据验证（结构合法性、业务合规性）
        ValidationResult validation = metadataValidator.validate(metadata);
        if (!validation.isValid()) {
            throw new MetadataValidationException("元数据验证失败", validation.getErrors());
        }
        
        // 3. 版本管理（处理新增/升级场景）
        EntityMetadata existing = metadataRegistry.findByApiNameAndTenantId(metadata.getApiName(), tenantId);
        EntityMetadata processed = versionController.processVersion(metadata, existing);
        
        // 4. 持久化存储
        EntityMetadata saved = metadataRegistry.save(processed);
        
        // 5. 缓存更新（多级缓存同步）
        cacheManager.put(tenantId, saved.getApiName(), saved);
        
        // 6. 发布元数据变更事件（触发表结构更新、索引重建等）
        eventPublisher.publishEvent(new MetadataChangedEvent(
            tenantId, saved.getApiName(), saved.getVersion(),
            existing != null ? existing.getVersion() : null
        ));
        
        log.info("元数据注册成功: {}:{}@{}", tenantId, saved.getApiName(), saved.getVersion());
        return saved;
    }
    
    /**
     * 元数据变更影响分析（避免破坏性变更）
     */
    public ImpactAnalysis analyzeMetadataImpact(String entityName, EntityMetadata newMetadata) {
        String tenantId = tenantContext.getCurrentTenantId();
        EntityMetadata current = getEntityMetadata(entityName);
        
        // 新增实体，无影响
        if (current == null) {
            return ImpactAnalysis.empty();
        }
        
        return metadataValidator.analyzeImpact(current, newMetadata);
    }
    
    /**
     * 创建新的实体实例
     * @param entityApiName 实体API名称
     * @return 动态实体实例
     */
    public DynamicSmartEntity createEntity(String entityApiName) {
        EntityMetadata entityMetadata = metadataRegistry.getEntityMetadata(entityApiName);
        if (entityMetadata == null) {
            throw new IllegalArgumentException("Entity not found: " + entityApiName);
        }
        
        DynamicSmartEntity entity = new DynamicSmartEntity();
        entity.setEntityApiName(entityApiName);
        entity.setEntityMetadata(entityMetadata);
        
        // 初始化默认值
        initializeDefaultValues(entity, entityMetadata);
        
        // 初始化瞬态字段
        entity.initializeTransientFields();
        
        log.debug("Created new entity instance: {}", entityApiName);
        return entity;
    }
    
    /**
     * 初始化实体字段默认值
     */
    private void initializeDefaultValues(DynamicSmartEntity entity, EntityMetadata entityMetadata) {
        if (entityMetadata.getFields() == null) {
            return;
        }
        
        for (FieldMetadata field : entityMetadata.getFields()) {
            // 跳过计算字段和虚拟字段
            if (field.isCalculated() || field.isVirtual()) {
                continue;
            }
            
            // 设置默认值
            if (field.getDefaultValue() != null) {
                try {
                    entity.setField(field.getApiName(), field.getDefaultValue());
                } catch (Exception e) {
                    log.warn("Failed to set default value for field {} in entity {}", 
                             field.getApiName(), entityMetadata.getApiName(), e);
                }
            }
        }
    }
    
    /**
     * 批量创建实体实例
     */
    public List<DynamicSmartEntity> createEntities(String entityApiName, int count) {
        List<DynamicSmartEntity> entities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entities.add(createEntity(entityApiName));
        }
        return entities;
    }
    
    /**
     * 从Map数据创建实体
     */
    public DynamicSmartEntity createEntityFromMap(String entityApiName, Map<String, Object> data) {
        DynamicSmartEntity entity = createEntity(entityApiName);
        if (data != null) {
            data.forEach((key, value) -> {
                try {
                    entity.setField(key, value);
                } catch (Exception e) {
                    log.warn("Failed to set field {} with value {} in entity {}", 
                             key, value, entityApiName, e);
                }
            });
        }
        return entity;
    }
    
    /**
     * 计算实体的计算字段值
     */
    public void calculateEntityFields(DynamicSmartEntity entity) {
        if (entity == null || entity.getEntityApiName() == null) {
            return;
        }
        
        List<FieldMetadata> calculatedFields = getMetadataRegistry()
                .getCalculatedFieldMetadata(entity.getEntityApiName());
        
        if (calculatedFields.isEmpty()) {
            return;
        }
        
        calculatedFields.forEach(field -> {
            try {
                Object value = calculationEngine.calculateField(entity, field);
                entity.setCalculatedField(field.getApiName(), value);
            } catch (Exception e) {
                log.error("Error calculating field {} for entity {}", 
                          field.getApiName(), entity.getEntityApiName(), e);
            }
        });
    }
    
    /**
     * 计算单个字段值
     */
    public Object calculateField(DynamicSmartEntity entity, String fieldApiName) {
        FieldMetadata fieldMetadata = getMetadataRegistry()
                .getFieldMetadata(entity.getEntityApiName(), fieldApiName);
        
        if (fieldMetadata == null || !fieldMetadata.isCalculated()) {
            throw new IllegalArgumentException("Field is not a calculated field: " + fieldApiName);
        }
        
        return calculationEngine.calculateField(entity, fieldMetadata);
    }
    
    /**
     * 验证实体数据
     */
    public ValidationResult validateEntity(DynamicSmartEntity entity) {
        ValidationResult result = new ValidationResult();
        
        if (entity == null || entity.getEntityApiName() == null) {
            result.addError("Entity is null or has no API name");
            return result;
        }
        
        EntityMetadata entityMetadata = getMetadataRegistry()
                .getEntityMetadata(entity.getEntityApiName());
        
        if (entityMetadata == null) {
            result.addError("Entity metadata not found for: " + entity.getEntityApiName());
            return result;
        }
        
        try {
            // 1. 计算实体字段值
            calculateEntityFields(entity);
            
            // 2. 执行字段级验证
            validateFields(entity, entityMetadata, result);
            
            // 3. 执行关系验证
            validateRelationships(entity, entityMetadata, result);
            
            // 4. 执行业务规则验证
            if (!result.hasErrors() && configuration.isBusinessRuleValidationEnabled()) {
                businessRuleEngine.executeValidationRules(entity, result);
            }
        } catch (Exception e) {
            log.error("Error during entity validation for {}", entity.getEntityApiName(), e);
            result.addError("Internal error during validation: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 验证实体字段
     */
    private void validateFields(DynamicSmartEntity entity, EntityMetadata entityMetadata, 
                               ValidationResult result) {
        if (entityMetadata.getFields() == null) {
            return;
        }
        
        for (FieldMetadata field : entityMetadata.getFields()) {
            // 跳过计算字段和虚拟字段的验证，它们的值由系统计算
            if (field.isCalculated() || field.isVirtual()) {
                continue;
            }
            
            Object value = entity.getField(field.getApiName());
            validationEngine.validateField(field, value, result);
        }
    }
    
    /**
     * 验证实体关系
     */
    private void validateRelationships(DynamicSmartEntity entity, EntityMetadata entityMetadata, 
                                      ValidationResult result) {
        if (entityMetadata.getRelationships() == null) {
            return;
        }
        
        for (RelationshipMetadata relationship : entityMetadata.getRelationships()) {
            validationEngine.validateRelationship(entity, relationship, result);
        }
    }
    
    /**
     * 批量验证实体
     */
    public Map<DynamicSmartEntity, ValidationResult> validateEntities(List<DynamicSmartEntity> entities) {
        Map<DynamicSmartEntity, ValidationResult> results = new HashMap<>();
        
        if (entities != null) {
            entities.forEach(entity -> {
                ValidationResult result = validateEntity(entity);
                results.put(entity, result);
            });
        }
        
        return results;
    }
    
    /**
     * 应用业务规则
     */
    public ValidationResult applyBusinessRules(DynamicSmartEntity entity, String eventType) {
        ValidationResult result = new ValidationResult();
        
        if (entity == null || entity.getEntityApiName() == null) {
            result.addError("Entity is null or has no API name");
            return result;
        }
        
        // 计算实体字段值
        calculateEntityFields(entity);
        
        // 执行验证规则
        if (configuration.isBusinessRuleValidationEnabled()) {
            businessRuleEngine.executeValidationRules(entity, result);
        }
        
        // 如果验证通过，执行操作规则
        if (result.isValid()) {
            businessRuleEngine.executeActionRules(entity, eventType);
        }
        
        return result;
    }
    
    /**
     * 注册业务规则
     * @param rule 业务规则元数据
     */
    public void registerBusinessRule(BusinessRuleMetadata rule) {
        businessRuleRegistry.registerRule(rule);
    }
    
    /**
     * 批量注册业务规则
     * @param rules 业务规则元数据列表
     */
    public void registerBusinessRules(List<BusinessRuleMetadata> rules) {
        businessRuleRegistry.registerRules(rules);
    }
    
    /**
     * 获取实体的业务规则
     * @param entityApiName 实体API名称
     * @return 业务规则元数据列表
     */
    public List<BusinessRuleMetadata> getBusinessRules(String entityApiName) {
        return businessRuleRegistry.getRulesByEntity(entityApiName);
    }
    
    /**
     * 准备实体数据（计算字段、应用规则等）
     */
    public void prepareEntity(DynamicSmartEntity entity, String eventType) {
        // 1. 计算实体字段值
        calculateEntityFields(entity);
        
        // 2. 应用业务规则
        applyBusinessRules(entity, eventType);
    }
    
    /**
     * 深度克隆实体
     */
    public DynamicSmartEntity cloneEntity(DynamicSmartEntity source) {
        if (source == null) {
            return null;
        }
        
        DynamicSmartEntity cloned = createEntity(source.getEntityApiName());
        
        // 复制字段值
        Map<String, Object> allFields = source.getAllFields();
        allFields.forEach((key, value) -> {
            try {
                // 深拷贝复杂对象
                Object clonedValue = cloneValue(value);
                cloned.setField(key, clonedValue);
            } catch (Exception e) {
                log.warn("Failed to clone field {} with value {} in entity {}", 
                         key, value, source.getEntityApiName(), e);
            }
        });
        
        return cloned;
    }
    
    /**
     * 克隆值对象（简化版本，实际可能需要更复杂的深拷贝实现）
     */
    private Object cloneValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || 
            value instanceof Boolean || value instanceof Enum) {
            return value; // 不可变类型直接返回
        }
        
        if (value instanceof Collection) {
            // 简单复制集合
            Collection<?> original = (Collection<?>) value;
            try {
                Collection<Object> copy = original.getClass().newInstance();
                for (Object item : original) {
                    copy.add(cloneValue(item));
                }
                return copy;
            } catch (Exception e) {
                log.warn("Failed to clone collection, using ArrayList instead", e);
                return ((Collection<?>) value).stream()
                        .map(this::cloneValue)
                        .collect(Collectors.toList());
            }
        }
        
        if (value instanceof Map) {
            // 简单复制Map
            Map<?, ?> original = (Map<?, ?>) value;
            try {
                Map<Object, Object> copy = original.getClass().newInstance();
                original.forEach((k, v) -> {
                    copy.put(cloneValue(k), cloneValue(v));
                });
                return copy;
            } catch (Exception e) {
                log.warn("Failed to clone map, using HashMap instead", e);
                Map<Object, Object> copy = new HashMap<>();
                original.forEach((k, v) -> {
                    copy.put(cloneValue(k), cloneValue(v));
                });
                return copy;
            }
        }
        
        // 对于其他复杂对象，这里简化处理，实际可能需要更复杂的序列化/反序列化方式
        return value;
    }
    
    /**
     * 导出实体为Map
     */
    public Map<String, Object> exportEntityToMap(DynamicSmartEntity entity, boolean includeCalculatedFields) {
        Map<String, Object> map = new HashMap<>();
        
        if (entity == null) {
            return map;
        }
        
        // 获取基础字段
        Map<String, Object> fields = entity.getAllFields();
        
        // 决定是否包含计算字段
        if (!includeCalculatedFields) {
            EntityMetadata entityMetadata = getMetadataRegistry()
                    .getEntityMetadata(entity.getEntityApiName());
            if (entityMetadata != null && entityMetadata.getFields() != null) {
                Set<String> calculatedFieldNames = entityMetadata.getFields().stream()
                        .filter(FieldMetadata::isCalculated)
                        .map(FieldMetadata::getApiName)
                        .collect(Collectors.toSet());
                
                for (String calculatedField : calculatedFieldNames) {
                    fields.remove(calculatedField);
                }
            }
        }
        
        map.putAll(fields);
        return map;
    }
    
    /**
     * 获取实体的所有关联实体
     */
    public Map<String, DynamicSmartEntity> getRelatedEntities(DynamicSmartEntity entity) {
        Map<String, DynamicSmartEntity> relatedEntities = new HashMap<>();
        
        if (entity == null || entity.getEntityApiName() == null) {
            return relatedEntities;
        }
        
        EntityMetadata entityMetadata = getMetadataRegistry()
                .getEntityMetadata(entity.getEntityApiName());
        
        if (entityMetadata == null || entityMetadata.getRelationships() == null) {
            return relatedEntities;
        }
        
        // 这里简化处理，实际需要根据关系定义加载关联实体
        // 在真实实现中，可能需要通过数据访问层加载关联实体
        for (RelationshipMetadata relationship : entityMetadata.getRelationships()) {
            // 示例实现，实际需要查询关联实体
            Object relatedId = entity.getField(relationship.getForeignKeyField());
            if (relatedId != null) {
                // 在真实实现中，这里应该通过ID查询关联实体
                log.debug("Need to load related entity: {} with ID: {}", 
                          relationship.getTargetEntityApiName(), relatedId);
            }
        }
        
        return relatedEntities;
    }
    
    /**
     * 检查实体字段是否可写
     */
    public boolean isFieldWritable(String entityApiName, String fieldApiName) {
        FieldMetadata fieldMetadata = getMetadataRegistry()
                .getFieldMetadata(entityApiName, fieldApiName);
        
        if (fieldMetadata == null) {
            return false;
        }
        
        // 计算字段和虚拟字段不可写
        if (fieldMetadata.isCalculated() || fieldMetadata.isVirtual()) {
            return false;
        }
        
        // 根据字段约束判断是否可写
        return !fieldMetadata.isReadOnly();
    }
    
    /**
     * 检查实体字段是否可读
     */
    public boolean isFieldReadable(String entityApiName, String fieldApiName) {
        FieldMetadata fieldMetadata = getMetadataRegistry()
                .getFieldMetadata(entityApiName, fieldApiName);
        
        if (fieldMetadata == null) {
            return false;
        }
        
        // 根据字段约束判断是否可读
        return !fieldMetadata.isHidden();
    }
    
    /**
     * 获取实体的显示名称
     */
    public String getEntityDisplayName(String entityApiName) {
        EntityMetadata entityMetadata = getMetadataRegistry().getEntityMetadata(entityApiName);
        return entityMetadata != null && entityMetadata.getName() != null ? 
               entityMetadata.getName() : entityApiName;
    }
    
    /**
     * 获取字段的显示名称
     */
    public String getFieldDisplayName(String entityApiName, String fieldApiName) {
        FieldMetadata fieldMetadata = getMetadataRegistry()
                .getFieldMetadata(entityApiName, fieldApiName);
        return fieldMetadata != null && fieldMetadata.getName() != null ? 
               fieldMetadata.getName() : fieldApiName;
    }
    
    /**
     * 关闭引擎资源
     */
    public void shutdown() {
        // 关闭业务规则引擎资源
        if (businessRuleEngine instanceof DefaultBusinessRuleEngine) {
            ((DefaultBusinessRuleEngine) businessRuleEngine).shutdown();
        }
        
        // 在真实实现中，这里应该释放其他资源，如关闭连接池等
        log.info("SmartMetadataEngine shutdown");
    }
}