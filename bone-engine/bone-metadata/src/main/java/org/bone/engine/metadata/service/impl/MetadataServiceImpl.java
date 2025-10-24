package org.bone.engine.metadata.service.impl;

import org.bone.engine.metadata.model.EntityMetadata;
import org.bone.engine.metadata.model.FieldMetadata;
import org.bone.engine.metadata.model.RelationshipMetadata;
import org.bone.engine.metadata.repository.MetadataRepository;
import org.bone.engine.metadata.service.MetadataService;
import org.bone.engine.metadata.util.MetadataUtils;
import org.bone.engine.metadata.validator.MetadataValidator;
import org.bone.engine.metadata.validator.MetadataValidator.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 元数据服务实现类 - 提供元数据管理的核心业务逻辑实现
 * 
 * @author Bone Engine Team
 */
@Service
public class MetadataServiceImpl implements MetadataService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetadataServiceImpl.class);
    
    private final MetadataRepository metadataRepository;
    private final MetadataValidator metadataValidator;
    
    @Autowired
    public MetadataServiceImpl(MetadataRepository metadataRepository, MetadataValidator metadataValidator) {
        this.metadataRepository = metadataRepository;
        this.metadataValidator = metadataValidator;
    }
    
    @Override
    public EntityMetadata createEntityMetadata(EntityMetadata entityMetadata) {
        Assert.notNull(entityMetadata, "Entity metadata cannot be null");
        Assert.hasText(entityMetadata.getApiName(), "API name cannot be empty");
        
        // 检查是否已存在
        if (metadataRepository.existsByApiName(entityMetadata.getApiName())) {
            throw new IllegalArgumentException("Entity metadata already exists: " + entityMetadata.getApiName());
        }
        
        // 设置默认值
        if (entityMetadata.getVersion() == null) {
            entityMetadata.setVersion("1.0.0");
        }
        
        // 保存元数据
        logger.info("Creating entity metadata: {}", entityMetadata.getApiName());
        return metadataRepository.save(entityMetadata);
    }
    
    @Override
    public Map<String, ValidationResult> batchCreateEntityMetadata(List<EntityMetadata> entityMetadataList) {
        Assert.notEmpty(entityMetadataList, "Entity metadata list cannot be empty");
        
        // 检查是否有重复的API名称
        Set<String> apiNames = new HashSet<>();
        for (EntityMetadata metadata : entityMetadataList) {
            if (!apiNames.add(metadata.getApiName())) {
                throw new IllegalArgumentException("Duplicate API name in batch: " + metadata.getApiName());
            }
            // 检查是否已存在
            if (metadataRepository.existsByApiName(metadata.getApiName())) {
                throw new IllegalArgumentException("Entity metadata already exists: " + metadata.getApiName());
            }
            // 设置默认值
            if (metadata.getVersion() == null) {
                metadata.setVersion("1.0.0");
            }
        }
        
        logger.info("Batch creating {} entity metadata records", entityMetadataList.size());
        return metadataRepository.saveAll(entityMetadataList);
    }
    
    @Override
    public EntityMetadata getEntityMetadata(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        logger.debug("Getting entity metadata: {}", apiName);
        return metadataRepository.getByApiName(apiName);
    }
    
    @Override
    public Optional<EntityMetadata> findEntityMetadata(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        logger.debug("Finding entity metadata: {}", apiName);
        return metadataRepository.findByApiName(apiName);
    }
    
    @Override
    public List<EntityMetadata> getAllEntityMetadata() {
        logger.debug("Getting all entity metadata");
        return metadataRepository.findAll();
    }
    
    @Override
    public List<EntityMetadata> queryEntityMetadata(MetadataRepository.MetadataCriteria criteria) {
        Assert.notNull(criteria, "Criteria cannot be null");
        logger.debug("Querying entity metadata with criteria");
        return metadataRepository.findByCriteria(criteria);
    }
    
    @Override
    public List<EntityMetadata> getEntityMetadataByDomain(String domain) {
        Assert.hasText(domain, "Domain cannot be empty");
        logger.debug("Getting entity metadata by domain: {}", domain);
        return metadataRepository.findByDomain(domain);
    }
    
    @Override
    public List<EntityMetadata> getEntityMetadataByType(String entityType) {
        Assert.hasText(entityType, "Entity type cannot be empty");
        logger.debug("Getting entity metadata by type: {}", entityType);
        return metadataRepository.findByEntityType(entityType);
    }
    
    @Override
    public EntityMetadata updateEntityMetadata(String apiName, EntityMetadata entityMetadata) {
        Assert.hasText(apiName, "API name cannot be empty");
        Assert.notNull(entityMetadata, "Entity metadata cannot be null");
        
        // 确保API名称一致
        if (!apiName.equals(entityMetadata.getApiName())) {
            throw new IllegalArgumentException("API name mismatch");
        }
        
        // 检查是否存在
        if (!metadataRepository.existsByApiName(apiName)) {
            throw new NoSuchElementException("Entity metadata not found: " + apiName);
        }
        
        logger.info("Updating entity metadata: {}", apiName);
        return metadataRepository.update(entityMetadata);
    }
    
    @Override
    public EntityMetadata partialUpdateEntityMetadata(String apiName, Map<String, Object> updates) {
        Assert.hasText(apiName, "API name cannot be empty");
        Assert.notEmpty(updates, "Updates cannot be empty");
        
        // 获取现有元数据
        EntityMetadata existingMetadata = getEntityMetadata(apiName);
        
        // 直接在当前方法中实现简单的部分更新逻辑，替代不存在的MetadataUtils.applyPartialUpdates方法
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();
            
            switch (fieldName) {
                case "description":
                    existingMetadata.setDescription((String) fieldValue);
                    break;
                case "version":
                    existingMetadata.setVersion((String) fieldValue);
                    break;
                case "primaryKeyField":
                    existingMetadata.setPrimaryKeyField((String) fieldValue);
                    break;
                // 可以根据需要添加更多字段的处理
                default:
                    logger.warn("Unsupported field for partial update: {}", fieldName);
                    break;
            }
        }
        
        logger.info("Partially updating entity metadata: {}", apiName);
        return metadataRepository.update(existingMetadata);
    }
    
    @Override
    public boolean deleteEntityMetadata(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        
        // 检查是否有其他实体依赖当前实体
        List<EntityMetadata> dependents = getDependents(apiName);
        if (!dependents.isEmpty()) {
            throw new IllegalStateException("Cannot delete entity with dependents: " + dependents.stream()
                    .map(EntityMetadata::getApiName).collect(Collectors.joining(", ")));
        }
        
        logger.info("Deleting entity metadata: {}", apiName);
        return metadataRepository.deleteByApiName(apiName);
    }
    
    @Override
    public int batchDeleteEntityMetadata(List<String> apiNames) {
        Assert.notEmpty(apiNames, "API names cannot be empty");
        
        // 检查依赖关系
        Set<String> allDependents = new HashSet<>();
        for (String apiName : apiNames) {
            List<EntityMetadata> dependents = getDependents(apiName);
            dependents.stream()
                    .map(EntityMetadata::getApiName)
                    .filter(name -> !apiNames.contains(name)) // 排除在删除列表中的实体
                    .forEach(allDependents::add);
        }
        
        if (!allDependents.isEmpty()) {
            throw new IllegalStateException("Cannot delete entities with external dependents: " + String.join(", ", allDependents));
        }
        
        logger.info("Batch deleting {} entity metadata records", apiNames.size());
        return metadataRepository.deleteByApiNames(apiNames);
    }
    
    @Override
    public boolean existsEntityMetadata(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        return metadataRepository.existsByApiName(apiName);
    }
    
    @Override
    public EntityMetadata addField(String apiName, FieldMetadata fieldMetadata) {
        Assert.hasText(apiName, "API name cannot be empty");
        Assert.notNull(fieldMetadata, "Field metadata cannot be null");
        Assert.hasText(fieldMetadata.getName(), "Field name cannot be empty");
        
        // 获取实体元数据
        EntityMetadata entityMetadata = getEntityMetadata(apiName);
        
        // 检查字段是否已存在 - 使用Map的values()方法获取字段值的流
        if (entityMetadata.getFields().values().stream().anyMatch(f -> f.getName().equals(fieldMetadata.getName()))) {
            throw new IllegalArgumentException("Field already exists: " + fieldMetadata.getName());
        }
        
        // 验证字段
        ValidationResult result = metadataValidator.validateFieldMetadata(fieldMetadata);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Invalid field metadata: " + result.getErrorMessage());
        }
        
        // 添加字段 - Map没有add方法，使用put方法
        entityMetadata.getFields().put(fieldMetadata.getName(), fieldMetadata);
        
        logger.info("Adding field {} to entity {}", fieldMetadata.getName(), apiName);
        return metadataRepository.update(entityMetadata);
    }
    
    @Override
    public EntityMetadata batchAddFields(String apiName, List<FieldMetadata> fieldMetadataList) {
        Assert.hasText(apiName, "API name cannot be empty");
        Assert.notEmpty(fieldMetadataList, "Field metadata list cannot be empty");
        
        // 获取实体元数据
        EntityMetadata entityMetadata = getEntityMetadata(apiName);
        
        // 检查字段是否已存在 - 使用Map的values()方法获取字段值的流
        Set<String> existingFieldNames = entityMetadata.getFields().values().stream()
                .map(FieldMetadata::getName)
                .collect(Collectors.toSet());
        
        for (FieldMetadata field : fieldMetadataList) {
            if (existingFieldNames.contains(field.getName())) {
                throw new IllegalArgumentException("Field already exists: " + field.getName());
            }
            // 验证字段
            ValidationResult result = metadataValidator.validateFieldMetadata(field);
            if (!result.isValid()) {
                throw new IllegalArgumentException("Invalid field metadata: " + field.getName() + ", " + result.getErrorMessage());
            }
        }
        
        // 批量添加字段 - Map没有addAll方法，需要逐个添加
        for (FieldMetadata fieldMetadata : fieldMetadataList) {
            entityMetadata.getFields().put(fieldMetadata.getName(), fieldMetadata);
        }
        
        logger.info("Batch adding {} fields to entity {}", fieldMetadataList.size(), apiName);
        return metadataRepository.update(entityMetadata);
    }
    
    @Override
    public EntityMetadata updateField(String apiName, String fieldName, FieldMetadata fieldMetadata) {
        Assert.hasText(apiName, "API name cannot be empty");
        Assert.hasText(fieldName, "Field name cannot be empty");
        Assert.notNull(fieldMetadata, "Field metadata cannot be null");
        
        // 获取实体元数据
        EntityMetadata entityMetadata = getEntityMetadata(apiName);
        
        // 查找字段 - 使用Map的values()方法获取字段值的流
        Optional<FieldMetadata> existingFieldOpt = entityMetadata.getFields().values().stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst();
        
        if (!existingFieldOpt.isPresent()) {
            throw new NoSuchElementException("Field not found: " + fieldName);
        }
        
        // 验证字段
        ValidationResult result = metadataValidator.validateFieldMetadata(fieldMetadata);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Invalid field metadata: " + result.getErrorMessage());
        }
        
        // 更新字段 - 使用Map的方式更新，而不是List的操作
        entityMetadata.getFields().put(fieldName, fieldMetadata);
        
        logger.info("Updating field {} in entity {}", fieldName, apiName);
        return metadataRepository.update(entityMetadata);
    }
    
    @Override
    public EntityMetadata deleteField(String apiName, String fieldName) {
        Assert.hasText(apiName, "API name cannot be empty");
        Assert.hasText(fieldName, "Field name cannot be empty");
        
        // 获取实体元数据
        EntityMetadata entityMetadata = getEntityMetadata(apiName);
        
        // 检查是否为主键字段 - 使用Map的values()方法获取字段值的流
        Optional<FieldMetadata> fieldOpt = entityMetadata.getFields().values().stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst();
        
        if (!fieldOpt.isPresent()) {
            throw new NoSuchElementException("Field not found: " + fieldName);
        }
        
        // 检查是否是主键字段
        if (entityMetadata.getPrimaryKeyField() != null && 
            entityMetadata.getPrimaryKeyField().equals(fieldName)) {
            throw new IllegalStateException("Cannot delete primary key field: " + fieldName);
        }
        
        // 删除字段 - Map没有removeIf方法，使用传统方式删除
        entityMetadata.getFields().remove(fieldName);
        
        logger.info("Deleting field {} from entity {}", fieldName, apiName);
        return metadataRepository.update(entityMetadata);
    }
    
    @Override
    public EntityMetadata addRelationship(String apiName, RelationshipMetadata relationshipMetadata) {
        Assert.hasText(apiName, "API name cannot be empty");
        Assert.notNull(relationshipMetadata, "Relationship metadata cannot be null");
        Assert.hasText(relationshipMetadata.getName(), "Relationship name cannot be empty");
        Assert.hasText(relationshipMetadata.getTargetEntity(), "Target entity cannot be empty");
        
        // 获取实体元数据
        EntityMetadata entityMetadata = getEntityMetadata(apiName);
        
        // 检查关系是否已存在
        if (entityMetadata.getRelationships().stream().anyMatch(r -> r.getName().equals(relationshipMetadata.getName()))) {
            throw new IllegalArgumentException("Relationship already exists: " + relationshipMetadata.getName());
        }
        
        // 检查目标实体是否存在
        if (!metadataRepository.existsByApiName(relationshipMetadata.getTargetEntity())) {
            throw new NoSuchElementException("Target entity not found: " + relationshipMetadata.getTargetEntity());
        }
        
        // 验证关系
        ValidationResult result = metadataValidator.validateRelationshipMetadata(relationshipMetadata);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Invalid relationship metadata: " + result.getErrorMessage());
        }
        
        // 添加关系
        entityMetadata.getRelationships().add(relationshipMetadata);
        
        logger.info("Adding relationship {} to entity {}", relationshipMetadata.getName(), apiName);
        return metadataRepository.update(entityMetadata);
    }
    
    @Override
    public EntityMetadata updateRelationship(String apiName, String relationshipName, RelationshipMetadata relationshipMetadata) {
        Assert.hasText(apiName, "API name cannot be empty");
        Assert.hasText(relationshipName, "Relationship name cannot be empty");
        Assert.notNull(relationshipMetadata, "Relationship metadata cannot be null");
        
        // 获取实体元数据
        EntityMetadata entityMetadata = getEntityMetadata(apiName);
        
        // 查找关系
        Optional<RelationshipMetadata> existingRelOpt = entityMetadata.getRelationships().stream()
                .filter(r -> r.getName().equals(relationshipName))
                .findFirst();
        
        if (!existingRelOpt.isPresent()) {
            throw new NoSuchElementException("Relationship not found: " + relationshipName);
        }
        
        // 检查目标实体是否存在
        if (!metadataRepository.existsByApiName(relationshipMetadata.getTargetEntity())) {
            throw new NoSuchElementException("Target entity not found: " + relationshipMetadata.getTargetEntity());
        }
        
        // 验证关系
        ValidationResult result = metadataValidator.validateRelationshipMetadata(relationshipMetadata);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Invalid relationship metadata: " + result.getErrorMessage());
        }
        
        // 更新关系
        int index = entityMetadata.getRelationships().indexOf(existingRelOpt.get());
        entityMetadata.getRelationships().set(index, relationshipMetadata);
        
        logger.info("Updating relationship {} in entity {}", relationshipName, apiName);
        return metadataRepository.update(entityMetadata);
    }
    
    @Override
    public EntityMetadata deleteRelationship(String apiName, String relationshipName) {
        Assert.hasText(apiName, "API name cannot be empty");
        Assert.hasText(relationshipName, "Relationship name cannot be empty");
        
        // 获取实体元数据
        EntityMetadata entityMetadata = getEntityMetadata(apiName);
        
        // 删除关系
        boolean removed = entityMetadata.getRelationships().removeIf(r -> r.getName().equals(relationshipName));
        if (!removed) {
            throw new NoSuchElementException("Relationship not found: " + relationshipName);
        }
        
        logger.info("Deleting relationship {} from entity {}", relationshipName, apiName);
        return metadataRepository.update(entityMetadata);
    }
    
    @Override
    public List<EntityMetadata> getDependencies(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        
        EntityMetadata entityMetadata = getEntityMetadata(apiName);
        Set<String> dependencyNames = entityMetadata.getRelationships().stream()
                .map(RelationshipMetadata::getTargetEntity)
                .collect(Collectors.toSet());
        
        List<EntityMetadata> dependencies = new ArrayList<>();
        for (String depName : dependencyNames) {
            try {
                dependencies.add(metadataRepository.getByApiName(depName));
            } catch (NoSuchElementException e) {
                logger.warn("Dependency not found: {}", depName);
            }
        }
        
        return dependencies;
    }
    
    @Override
    public List<EntityMetadata> getDependents(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        return metadataRepository.findByRelatedEntity(apiName);
    }
    
    @Override
    public ValidationResult validateEntityMetadata(EntityMetadata entityMetadata) {
        Assert.notNull(entityMetadata, "Entity metadata cannot be null");
        return metadataValidator.validateEntityMetadata(entityMetadata);
    }
    
    @Override
    public ValidationResult validateFieldMetadata(FieldMetadata fieldMetadata) {
        Assert.notNull(fieldMetadata, "Field metadata cannot be null");
        return metadataValidator.validateFieldMetadata(fieldMetadata);
    }
    
    @Override
    public ValidationResult validateRelationshipMetadata(RelationshipMetadata relationshipMetadata) {
        Assert.notNull(relationshipMetadata, "Relationship metadata cannot be null");
        return metadataValidator.validateRelationshipMetadata(relationshipMetadata);
    }
    
    @Override
    public String exportEntityMetadata(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        return metadataRepository.exportToJson(apiName);
    }
    
    @Override
    public List<String> exportEntityMetadataList(List<String> apiNames) {
        Assert.notEmpty(apiNames, "API names cannot be empty");
        List<String> jsonList = new ArrayList<>();
        for (String apiName : apiNames) {
            jsonList.add(exportEntityMetadata(apiName));
        }
        return jsonList;
    }
    
    @Override
    public List<String> exportAllEntityMetadata() {
        return metadataRepository.exportAllToJson();
    }
    
    @Override
    public EntityMetadata importEntityMetadata(String json) {
        Assert.hasText(json, "JSON cannot be empty");
        return metadataRepository.importFromJson(json);
    }
    
    @Override
    public Map<String, ValidationResult> batchImportEntityMetadata(List<String> jsonList) {
        Assert.notEmpty(jsonList, "JSON list cannot be empty");
        return metadataRepository.importAllFromJson(jsonList);
    }
    
    @Override
    public EntityMetadata copyEntityMetadata(String sourceApiName, String newApiName, String newLabel) {
        Assert.hasText(sourceApiName, "Source API name cannot be empty");
        Assert.hasText(newApiName, "New API name cannot be empty");
        Assert.hasText(newLabel, "New label cannot be empty");
        
        // 检查新API名称是否已存在
        if (metadataRepository.existsByApiName(newApiName)) {
            throw new IllegalArgumentException("New API name already exists: " + newApiName);
        }
        
        // 获取源实体元数据
        EntityMetadata sourceMetadata = getEntityMetadata(sourceApiName);
        
        // 创建副本
        EntityMetadata newMetadata = new EntityMetadata();
        newMetadata.setApiName(newApiName);
        newMetadata.setLabel(newLabel);
        newMetadata.setDomain(sourceMetadata.getDomain());
        newMetadata.setEntityType(sourceMetadata.getEntityType());
        newMetadata.setDescription(sourceMetadata.getDescription());
        newMetadata.setVersion("1.0.0"); // 重置版本
        
        // 复制字段
        Map<String, FieldMetadata> newFields = new HashMap<>();
        for (Map.Entry<String, FieldMetadata> entry : sourceMetadata.getFields().entrySet()) {
            FieldMetadata field = entry.getValue();
            FieldMetadata newField = new FieldMetadata();
            // 复制字段属性
            newField.setName(field.getName());
            newField.setLabel(field.getLabel());
            newField.setType(field.getType());
            newField.setDescription(field.getDescription());
            // 修复不存在的方法调用
            // newField.setPrimaryKey() 不存在，所以注释掉
            // newField.setRequired() 不存在，使用setter方法
            newField.setRequired(field.getRequired());
            // newField.setUnique() 不存在，所以注释掉
            newField.setDefaultValue(field.getDefaultValue());
            newField.setMaxLength(field.getMaxLength());
            // newField.setMinLength() 不存在，所以注释掉
            // newField.setPattern() 不存在，所以注释掉
            // newField.setEnumValues() 不存在，所以注释掉
            // newField.setValidationRules() 不存在，所以注释掉
            // newField.setIndexConfig() 不存在，所以注释掉
            // newField.setAutoFill() 不存在，所以注释掉
            // newField.setEncryption() 不存在，所以注释掉
            newFields.put(entry.getKey(), newField);
        }
        newMetadata.setFields(newFields);
        
        // 复制关系（注意：关系保持不变，指向原始的目标实体）
        List<RelationshipMetadata> newRelationships = sourceMetadata.getRelationships().stream()
                .map(rel -> {
                    RelationshipMetadata newRel = new RelationshipMetadata();
                    newRel.setName(rel.getName());
                    newRel.setLabel(rel.getLabel());
                    newRel.setType(rel.getType());
                    newRel.setTargetEntity(rel.getTargetEntity());
                    newRel.setDescription(rel.getDescription());
                    newRel.setCascade(rel.getCascade());
                    newRel.setFetchType(rel.getFetchType());
                    // 使用required字段的相反值来替代isOptional()
                    newRel.setRequired(rel.getRequired());
                    // 为mappedBy设置一个默认值，因为RelationshipMetadata类中没有对应的字段
                    // newRel.setMappedBy()方法不存在，所以注释掉这行
                    return newRel;
                })
                .collect(Collectors.toList());
        newMetadata.setRelationships(newRelationships);
        
        logger.info("Copying entity metadata from {} to {}", sourceApiName, newApiName);
        return metadataRepository.save(newMetadata);
    }
    
    @Override
    public Map<String, Object> compareEntityMetadata(String apiName1, String apiName2) {
        Assert.hasText(apiName1, "First API name cannot be empty");
        Assert.hasText(apiName2, "Second API name cannot be empty");
        
        EntityMetadata metadata1 = getEntityMetadata(apiName1);
        EntityMetadata metadata2 = getEntityMetadata(apiName2);
        
        // 直接在当前方法中实现简单的比较逻辑，替代不存在的MetadataUtils.compareEntityMetadata方法
        Map<String, Object> comparison = new HashMap<>();
        comparison.put("apiName1", apiName1);
        comparison.put("apiName2", apiName2);
        
        // 比较基本信息 - EntityMetadata没有getName()方法，假设使用apiName作为名称
        boolean hasDifferentName = !Objects.equals(apiName1, apiName2);
        boolean hasDifferentDescription = !Objects.equals(metadata1.getDescription(), metadata2.getDescription());
        boolean hasDifferentVersion = !Objects.equals(metadata1.getVersion(), metadata2.getVersion());
        
        comparison.put("hasNameDifference", hasDifferentName);
        comparison.put("hasDescriptionDifference", hasDifferentDescription);
        comparison.put("hasVersionDifference", hasDifferentVersion);
        
        // 比较字段数量
        comparison.put("fieldCount1", metadata1.getFields().size());
        comparison.put("fieldCount2", metadata2.getFields().size());
        comparison.put("hasDifferentFieldCount", metadata1.getFields().size() != metadata2.getFields().size());
        
        return comparison;
    }
    
    @Override
    public String createVersionSnapshot(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        
        EntityMetadata metadata = getEntityMetadata(apiName);
        // 在实际实现中，这里应该创建版本快照并存储
        // 这里简化处理，仅更新版本号
        String currentVersion = metadata.getVersion();
        String newVersion = incrementVersion(currentVersion);
        metadata.setVersion(newVersion);
        
        metadataRepository.update(metadata);
        logger.info("Created version snapshot for {}: {}", apiName, newVersion);
        return newVersion;
    }
    
    private String incrementVersion(String version) {
        // 简化的版本号递增逻辑
        String[] parts = version.split("\\.");
        if (parts.length == 3) {
            int patch = Integer.parseInt(parts[2]) + 1;
            return parts[0] + "." + parts[1] + "." + patch;
        }
        return version + ".1";
    }
    
    @Override
    public EntityMetadata rollbackToVersion(String apiName, String version) {
        // 在实际实现中，这里应该从版本历史中恢复指定版本
        // 这里简化处理，仅返回当前版本
        logger.warn("Version rollback not fully implemented, returning current version");
        return getEntityMetadata(apiName);
    }
    
    @Override
    public List<Map<String, Object>> getEntityVersions(String apiName) {
        // 在实际实现中，这里应该返回版本历史
        // 这里简化处理，仅返回当前版本
        EntityMetadata metadata = getEntityMetadata(apiName);
        List<Map<String, Object>> versions = new ArrayList<>();
        Map<String, Object> versionInfo = new HashMap<>();
        versionInfo.put("version", metadata.getVersion());
        versionInfo.put("timestamp", System.currentTimeMillis());
        versionInfo.put("description", "Current version");
        versions.add(versionInfo);
        return versions;
    }
    
    @Override
    public void refreshMetadataCache() {
        logger.info("Refreshing metadata cache");
        metadataRepository.refreshCache();
    }
    
    @Override
    public void clearEntityCache(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        logger.info("Clearing cache for entity: {}", apiName);
        metadataRepository.clearCache(apiName);
    }
    
    @Override
    public Map<String, Object> getMetadataStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 总实体数
        long totalEntities = metadataRepository.count();
        stats.put("totalEntities", totalEntities);
        
        // 按域统计
        Map<String, Long> entitiesByDomain = new HashMap<>();
        List<EntityMetadata> allMetadata = metadataRepository.findAll();
        for (EntityMetadata metadata : allMetadata) {
            entitiesByDomain.put(metadata.getDomain(), 
                    entitiesByDomain.getOrDefault(metadata.getDomain(), 0L) + 1);
        }
        stats.put("entitiesByDomain", entitiesByDomain);
        
        // 按实体类型统计
        Map<String, Long> entitiesByType = new HashMap<>();
        for (EntityMetadata metadata : allMetadata) {
            entitiesByType.put(metadata.getEntityType(), 
                    entitiesByType.getOrDefault(metadata.getEntityType(), 0L) + 1);
        }
        stats.put("entitiesByType", entitiesByType);
        
        // 字段统计
        int totalFields = 0;
        int totalRelationships = 0;
        for (EntityMetadata metadata : allMetadata) {
            totalFields += metadata.getFields().size();
            totalRelationships += metadata.getRelationships().size();
        }
        stats.put("totalFields", totalFields);
        stats.put("totalRelationships", totalRelationships);
        
        if (totalEntities > 0) {
            stats.put("avgFieldsPerEntity", (double) totalFields / totalEntities);
            stats.put("avgRelationshipsPerEntity", (double) totalRelationships / totalEntities);
        }
        
        logger.debug("Generating metadata statistics");
        return stats;
    }
    
    @Override
    public MetadataRepository.ConsistencyCheckResult checkMetadataConsistency() {
        logger.info("Checking metadata consistency");
        return metadataRepository.checkConsistency();
    }
    
    @Override
    public MetadataRepository.ConsistencyCheckResult repairMetadataConsistency() {
        logger.info("Repairing metadata consistency");
        return metadataRepository.repairConsistency();
    }
    
    @Override
    public String generateEntityDocumentation(String apiName) {
        Assert.hasText(apiName, "API name cannot be empty");
        
        EntityMetadata metadata = getEntityMetadata(apiName);
        StringBuilder doc = new StringBuilder();
        
        // 实体基本信息
        doc.append("# Entity Metadata Documentation\n\n");
        doc.append("## Entity: " + metadata.getLabel() + " (" + metadata.getApiName() + ")\n\n");
        doc.append("**Domain:** " + metadata.getDomain() + "\n");
        doc.append("**Type:** " + metadata.getEntityType() + "\n");
        doc.append("**Version:** " + metadata.getVersion() + "\n\n");
        
        if (metadata.getDescription() != null) {
            doc.append("**Description:** " + metadata.getDescription() + "\n\n");
        }
        
        // 字段信息
        doc.append("## Fields\n\n");
        doc.append("| Name | Label | Type | Required | Primary Key | Unique | Description |\n");
        doc.append("|------|-------|------|----------|-------------|--------|-------------|\n");
        
        for (FieldMetadata field : metadata.getFields().values()) {
            // 检查是否是主键字段
            boolean isPrimaryKey = metadata.getPrimaryKeyField() != null && 
                                  metadata.getPrimaryKeyField().equals(field.getName());
            
            doc.append("| " + field.getName() + " ")
               .append("| " + (field.getLabel() != null ? field.getLabel() : "") + " ")
               .append("| " + field.getType() + " ")
               .append("| " + field.getRequired() + " ")
               .append("| " + isPrimaryKey + " ")
               .append("| " + false + " ") // 简化处理，假设没有唯一约束
               .append("| " + (field.getDescription() != null ? field.getDescription() : "") + " |\n");
        }
        
        // 关系信息
        doc.append("\n## Relationships\n\n");
        doc.append("| Name | Label | Type | Target Entity | Cascade | Description |\n");
        doc.append("|------|-------|------|---------------|---------|-------------|\n");
        
        for (RelationshipMetadata relationship : metadata.getRelationships()) {
            doc.append("| " + relationship.getName() + " ")
               .append("| " + (relationship.getLabel() != null ? relationship.getLabel() : "") + " ")
               .append("| " + relationship.getType() + " ")
               .append("| " + relationship.getTargetEntity() + " ")
               .append("| " + (relationship.getCascade() != null ? String.join(", ", relationship.getCascade()) : "") + " ")
               .append("| " + (relationship.getDescription() != null ? relationship.getDescription() : "") + " |\n");
        }
        
        logger.info("Generating documentation for entity: {}", apiName);
        return doc.toString();
    }
    
    @Override
    public Map<String, String> generateAllEntityDocumentation() {
        List<EntityMetadata> allMetadata = metadataRepository.findAll();
        Map<String, String> docs = new HashMap<>();
        
        for (EntityMetadata metadata : allMetadata) {
            docs.put(metadata.getApiName(), generateEntityDocumentation(metadata.getApiName()));
        }
        
        logger.info("Generating documentation for all {} entities", allMetadata.size());
        return docs;
    }
    
    @Override
    public List<FieldMetadata> getRecommendedFields(String apiName) {
        // 在实际实现中，这里应该根据实体类型和最佳实践推荐字段
        // 这里返回空列表作为示例
        logger.warn("Field recommendation not fully implemented");
        return Collections.emptyList();
    }
    
    @Override
    public List<RelationshipMetadata> getRecommendedRelationships(String apiName) {
        // 在实际实现中，这里应该根据实体类型和现有实体推荐关系
        // 这里返回空列表作为示例
        logger.warn("Relationship recommendation not fully implemented");
        return Collections.emptyList();
    }
    
    @Override
    public Map<String, Object> analyzeRelationshipGraph() {
        Map<String, Object> graphData = new HashMap<>();
        List<EntityMetadata> allMetadata = metadataRepository.findAll();
        
        // 构建节点和边
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        
        // 添加所有实体作为节点
        for (EntityMetadata metadata : allMetadata) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", metadata.getApiName());
            node.put("label", metadata.getLabel());
            node.put("domain", metadata.getDomain());
            node.put("type", metadata.getEntityType());
            nodes.add(node);
        }
        
        // 添加所有关系作为边
        for (EntityMetadata metadata : allMetadata) {
            for (RelationshipMetadata rel : metadata.getRelationships()) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("source", metadata.getApiName());
                edge.put("target", rel.getTargetEntity());
                edge.put("name", rel.getName());
                edge.put("type", rel.getType());
                edges.add(edge);
            }
        }
        
        graphData.put("nodes", nodes);
        graphData.put("edges", edges);
        graphData.put("totalNodes", nodes.size());
        graphData.put("totalEdges", edges.size());
        
        logger.info("Analyzing relationship graph with {} nodes and {} edges", nodes.size(), edges.size());
        return graphData;
    }
    
    @Override
    public List<String> optimizeEntityStructure(String apiName) {
        // 在实际实现中，这里应该分析实体结构并提供优化建议
        // 这里返回示例建议
        List<String> suggestions = new ArrayList<>();
        suggestions.add("Optimize entity structure recommendation is not fully implemented");
        return suggestions;
    }
    
    @Override
    public List<String> detectPotentialIssues(String apiName) {
        EntityMetadata metadata = getEntityMetadata(apiName);
        List<String> issues = new ArrayList<>();
        
        // 检查是否有主键
        boolean hasPrimaryKey = metadata.getPrimaryKeyFieldMetadata() != null;
        if (!hasPrimaryKey && metadata.getPrimaryKeyField() != null) {
            hasPrimaryKey = metadata.getFields() != null && 
                           metadata.getFields().containsKey(metadata.getPrimaryKeyField());
        }
        if (!hasPrimaryKey) {
            issues.add("No primary key field defined");
        }
        
        // 检查是否有重复的字段名
        Set<String> fieldNames = new HashSet<>();
        for (FieldMetadata field : metadata.getFields().values()) {
            if (!fieldNames.add(field.getName())) {
                issues.add("Duplicate field name: " + field.getName());
            }
        }
        
        // 检查是否有重复的关系名
        Set<String> relationshipNames = new HashSet<>();
        for (RelationshipMetadata rel : metadata.getRelationships()) {
            if (!relationshipNames.add(rel.getName())) {
                issues.add("Duplicate relationship name: " + rel.getName());
            }
        }
        
        // 检查关系目标实体是否存在
        for (RelationshipMetadata rel : metadata.getRelationships()) {
            if (!metadataRepository.existsByApiName(rel.getTargetEntity())) {
                issues.add("Relationship target entity not found: " + rel.getTargetEntity());
            }
        }
        
        logger.info("Detecting potential issues for entity: {}", apiName);
        return issues;
    }
    
    @Override
    public Map<String, ValidationResult> validateByBusinessRules(String apiName, List<String> businessRules) {
        // 在实际实现中，这里应该根据业务规则验证实体元数据
        // 这里返回空映射作为示例
        logger.warn("Business rule validation not fully implemented");
        return Collections.emptyMap();
    }
}