package org.bone.engine.metadata.repository.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bone.engine.metadata.model.EntityMetadata;
import org.bone.engine.metadata.model.FieldMetadata;
import org.bone.engine.metadata.repository.MetadataRepository;
import org.bone.engine.metadata.validator.MetadataValidator;
import org.bone.engine.metadata.validator.MetadataValidator.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存元数据仓库实现 - 提供基于内存的元数据存储
 * 
 * @author Bone Engine Team
 */
@Component
public class InMemoryMetadataRepository implements MetadataRepository {
    
    private final Map<String, EntityMetadata> metadataStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MetadataValidator metadataValidator;
    
    public InMemoryMetadataRepository(MetadataValidator metadataValidator) {
        this.metadataValidator = metadataValidator;
    }
    
    @Override
    public EntityMetadata save(EntityMetadata entityMetadata) {
        // 验证元数据
        ValidationResult validationResult = metadataValidator.validateEntityMetadata(entityMetadata);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("Invalid entity metadata: " + validationResult.getErrorMessage());
        }
        
        // 保存到内存存储
        metadataStore.put(entityMetadata.getApiName(), entityMetadata);
        return entityMetadata;
    }
    
    @Override
    public Map<String, ValidationResult> saveAll(List<EntityMetadata> entityMetadataList) {
        Map<String, ValidationResult> resultMap = new HashMap<>();
        
        for (EntityMetadata metadata : entityMetadataList) {
            ValidationResult validationResult = metadataValidator.validateEntityMetadata(metadata);
            resultMap.put(metadata.getApiName(), validationResult);
            
            if (validationResult.isValid()) {
                metadataStore.put(metadata.getApiName(), metadata);
            }
        }
        
        return resultMap;
    }
    
    @Override
    public Optional<EntityMetadata> findByApiName(String apiName) {
        return Optional.ofNullable(metadataStore.get(apiName));
    }
    
    @Override
    public EntityMetadata getByApiName(String apiName) {
        EntityMetadata metadata = metadataStore.get(apiName);
        if (metadata == null) {
            throw new NoSuchElementException("Entity metadata not found: " + apiName);
        }
        return metadata;
    }
    
    @Override
    public List<EntityMetadata> findAll() {
        return new ArrayList<>(metadataStore.values());
    }
    
    @Override
    public List<EntityMetadata> findByCriteria(MetadataCriteria criteria) {
        List<EntityMetadata> results = new ArrayList<>(metadataStore.values());
        
        // 应用过滤条件
        if (criteria instanceof DefaultMetadataCriteria) {
            DefaultMetadataCriteria defaultCriteria = (DefaultMetadataCriteria) criteria;
            
            // 过滤API名称
            if (defaultCriteria.getApiNameLike() != null) {
                results = results.stream()
                        .filter(m -> m.getApiName().toLowerCase().contains(defaultCriteria.getApiNameLike().toLowerCase()))
                        .collect(Collectors.toList());
            }
            
            // 过滤业务域
            if (defaultCriteria.getDomain() != null) {
                results = results.stream()
                        .filter(m -> defaultCriteria.getDomain().equals(m.getDomain()))
                        .collect(Collectors.toList());
            }
            
            // 过滤实体类型
            if (defaultCriteria.getEntityType() != null) {
                results = results.stream()
                        .filter(m -> defaultCriteria.getEntityType().equals(m.getEntityType()))
                        .collect(Collectors.toList());
            }
            
            // 过滤包含特定字段
            if (defaultCriteria.getContainsField() != null) {
                results = results.stream()
                        .filter(m -> m.getFields().stream().anyMatch(f -> defaultCriteria.getContainsField().equals(f.getName())))
                        .collect(Collectors.toList());
            }
            
            // 过滤包含特定字段类型
            if (defaultCriteria.getContainsFieldType() != null) {
                results = results.stream()
                        .filter(m -> m.getFields().stream().anyMatch(f -> defaultCriteria.getContainsFieldType().equals(f.getType())))
                        .collect(Collectors.toList());
            }
            
            // 过滤包含特定关系
            if (defaultCriteria.getContainsRelationship() != null) {
                results = results.stream()
                        .filter(m -> m.getRelationships().stream().anyMatch(r -> defaultCriteria.getContainsRelationship().equals(r.getName())))
                        .collect(Collectors.toList());
            }
            
            // 过滤包含特定目标实体关系
            if (defaultCriteria.getContainsTargetEntity() != null) {
                results = results.stream()
                        .filter(m -> m.getRelationships().stream().anyMatch(r -> defaultCriteria.getContainsTargetEntity().equals(r.getTargetEntity())))
                        .collect(Collectors.toList());
            }
            
            // 过滤AI增强配置
            if (defaultCriteria.getAiEnhancementEnabled() != null) {
                // 跳过isEnabled()调用，只检查AI增强配置是否存在
                results = results.stream()
                        .filter(m -> m.getAiEnhancement() != null)
                        .collect(Collectors.toList());
            }
            
            // 过滤知识图谱配置
            if (defaultCriteria.getKnowledgeGraphEnabled() != null) {
                // 跳过isEnabled()调用，只检查知识图谱配置是否存在
                results = results.stream()
                        .filter(m -> m.getKgConfig() != null)
                        .collect(Collectors.toList());
            }
            
            // 过滤MCP接口配置
            if (defaultCriteria.getMcpInterfaceEnabled() != null) {
                // 跳过isEnabled()调用，只检查MCP接口是否存在
                results = results.stream()
                        .filter(m -> m.getMcpInterface() != null)
                        .collect(Collectors.toList());
            }
            
            // 应用排序
            if (defaultCriteria.getSortField() != null && defaultCriteria.getSortDirection() != null) {
                results.sort((m1, m2) -> {
                    int comparison = 0;
                    switch (defaultCriteria.getSortField()) {
                        case "apiName":
                            comparison = m1.getApiName().compareTo(m2.getApiName());
                            break;
                        case "domain":
                            comparison = m1.getDomain().compareTo(m2.getDomain());
                            break;
                        case "entityType":
                            comparison = m1.getEntityType().compareTo(m2.getEntityType());
                            break;
                        case "version":
                            comparison = m1.getVersion().compareTo(m2.getVersion());
                            break;
                        default:
                            break;
                    }
                    return defaultCriteria.getSortDirection() == SortDirection.DESC ? -comparison : comparison;
                });
            }
            
            // 应用分页
            if (defaultCriteria.getPage() != null && defaultCriteria.getSize() != null) {
                int fromIndex = Math.max(0, defaultCriteria.getPage() * defaultCriteria.getSize());
                int toIndex = Math.min(results.size(), fromIndex + defaultCriteria.getSize());
                if (fromIndex < toIndex) {
                    results = results.subList(fromIndex, toIndex);
                } else {
                    results = Collections.emptyList();
                }
            }
        }
        
        return results;
    }
    
    @Override
    public List<EntityMetadata> findByDomain(String domain) {
        return metadataStore.values().stream()
                .filter(m -> domain.equals(m.getDomain()))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<EntityMetadata> findByEntityType(String entityType) {
        return metadataStore.values().stream()
                .filter(m -> entityType.equals(m.getEntityType()))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<EntityMetadata> findByFieldType(String fieldType) {
        return metadataStore.values().stream()
                .filter(m -> m.getFields().values().stream().anyMatch(f -> fieldType.equals(f.getType())))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<EntityMetadata> findByRelatedEntity(String targetEntityApiName) {
        return metadataStore.values().stream()
                .filter(m -> m.getRelationships().stream().anyMatch(r -> targetEntityApiName.equals(r.getTargetEntity())))
                .collect(Collectors.toList());
    }
    
    @Override
    public EntityMetadata update(EntityMetadata entityMetadata) {
        // 验证元数据
        ValidationResult validationResult = metadataValidator.validateEntityMetadata(entityMetadata);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("Invalid entity metadata: " + validationResult.getErrorMessage());
        }
        
        // 检查是否存在
        if (!metadataStore.containsKey(entityMetadata.getApiName())) {
            throw new NoSuchElementException("Entity metadata not found: " + entityMetadata.getApiName());
        }
        
        // 更新内存存储
        metadataStore.put(entityMetadata.getApiName(), entityMetadata);
        return entityMetadata;
    }
    
    @Override
    public boolean deleteByApiName(String apiName) {
        return metadataStore.remove(apiName) != null;
    }
    
    @Override
    public int deleteByApiNames(List<String> apiNames) {
        int deletedCount = 0;
        for (String apiName : apiNames) {
            if (metadataStore.remove(apiName) != null) {
                deletedCount++;
            }
        }
        return deletedCount;
    }
    
    @Override
    public boolean existsByApiName(String apiName) {
        return metadataStore.containsKey(apiName);
    }
    
    @Override
    public long count() {
        return metadataStore.size();
    }
    
    @Override
    public long countByDomain(String domain) {
        return metadataStore.values().stream()
                .filter(m -> domain.equals(m.getDomain()))
                .count();
    }
    
    @Override
    public void refreshCache() {
        // 内存存储无需刷新缓存
        System.out.println("Cache refreshed for metadata repository");
    }
    
    @Override
    public void clearCache(String apiName) {
        // 内存存储无需清除缓存
        System.out.println("Cache cleared for entity: " + apiName);
    }
    
    @Override
    public String exportToJson(String apiName) {
        EntityMetadata metadata = getByApiName(apiName);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to export metadata to JSON", e);
        }
    }
    
    @Override
    public List<String> exportAllToJson() {
        List<String> jsonList = new ArrayList<>();
        for (EntityMetadata metadata : metadataStore.values()) {
            try {
                jsonList.add(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to export metadata to JSON", e);
            }
        }
        return jsonList;
    }
    
    @Override
    public EntityMetadata importFromJson(String json) {
        try {
            EntityMetadata metadata = objectMapper.readValue(json, EntityMetadata.class);
            return save(metadata);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to import metadata from JSON", e);
        }
    }
    
    @Override
    public Map<String, ValidationResult> importAllFromJson(List<String> jsonList) {
        List<EntityMetadata> metadataList = new ArrayList<>();
        for (String json : jsonList) {
            try {
                EntityMetadata metadata = objectMapper.readValue(json, EntityMetadata.class);
                metadataList.add(metadata);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to import metadata from JSON", e);
            }
        }
        return saveAll(metadataList);
    }
    
    @Override
    public ConsistencyCheckResult checkConsistency() {
        List<Inconsistency> inconsistencies = new ArrayList<>();
        
        // 检查每个实体元数据
        for (EntityMetadata metadata : metadataStore.values()) {
            // 检查是否有主键字段
            boolean hasPrimaryKey = false;
            for (FieldMetadata field : metadata.getFields().values()) {
                if (field.getPrimaryKey() != null && field.getPrimaryKey()) {
                    hasPrimaryKey = true;
                    break;
                }
            }
            if (!hasPrimaryKey) {
                inconsistencies.add(new DefaultInconsistency(
                        InconsistencyType.MISSING_PRIMARY_KEY,
                        metadata.getApiName(),
                        "Entity does not have a primary key field"
                ));
            }
            
            // 检查关系的目标实体是否存在
            for (var relationship : metadata.getRelationships()) {
                if (!metadataStore.containsKey(relationship.getTargetEntity())) {
                    inconsistencies.add(new DefaultInconsistency(
                            InconsistencyType.RELATIONSHIP_TARGET_NOT_FOUND,
                            metadata.getApiName(),
                            "Relationship target entity not found: " + relationship.getTargetEntity()
                    ));
                }
            }
            
            // 检查API名称格式
            if (!metadata.getApiName().matches("^[a-z][a-z0-9_]*$")) {
                inconsistencies.add(new DefaultInconsistency(
                        InconsistencyType.INVALID_API_NAME_FORMAT,
                        metadata.getApiName(),
                        "Invalid API name format, must start with lowercase letter and contain only lowercase letters, numbers and underscores"
                ));
            }
            
            // 检查字段名称格式
            for (var field : metadata.getFields().values()) {
                if (!field.getName().matches("^[a-z][a-z0-9_]*$")) {
                    inconsistencies.add(new DefaultInconsistency(
                            InconsistencyType.INVALID_FIELD_NAME_FORMAT,
                            metadata.getApiName(),
                            "Invalid field name format: " + field.getName() + ", must start with lowercase letter and contain only lowercase letters, numbers and underscores"
                    ));
                }
            }
        }
        
        return new DefaultConsistencyCheckResult(inconsistencies.isEmpty(), inconsistencies);
    }
    
    @Override
    public ConsistencyCheckResult repairConsistency() {
        ConsistencyCheckResult result = checkConsistency();
        List<Inconsistency> inconsistencies = result.getInconsistencies();
        
        // 尝试修复不一致问题
        for (Inconsistency inconsistency : inconsistencies) {
            if (inconsistency instanceof DefaultInconsistency) {
                DefaultInconsistency defaultInconsistency = (DefaultInconsistency) inconsistency;
                
                switch (inconsistency.getType()) {
                    case MISSING_PRIMARY_KEY:
                        // 尝试添加ID字段作为主键
                        try {
                            EntityMetadata metadata = getByApiName(inconsistency.getEntityApiName());
                            // 这里应该添加修复逻辑，为了演示简化处理
                            defaultInconsistency.setFixMessage("Manual repair required: add a primary key field");
                        } catch (Exception e) {
                            defaultInconsistency.setFixMessage("Failed to repair: " + e.getMessage());
                        }
                        break;
                    default:
                        defaultInconsistency.setFixMessage("Manual repair required");
                        break;
                }
            }
        }
        
        return result;
    }
}