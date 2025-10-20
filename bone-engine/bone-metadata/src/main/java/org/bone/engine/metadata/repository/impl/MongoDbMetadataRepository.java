package org.bone.engine.metadata.repository.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bone.engine.metadata.model.EntityMetadata;
import org.bone.engine.metadata.repository.MetadataRepository;
import org.bone.engine.metadata.validator.MetadataValidator;
import org.bone.engine.metadata.validator.MetadataValidator.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MongoDB元数据仓库实现 - 提供基于MongoDB的元数据持久化存储
 * 
 * @author Bone Engine Team
 */
@Component
@Primary
public class MongoDbMetadataRepository implements MetadataRepository {
    
    private static final String COLLECTION_NAME = "entity_metadata";
    private static final String ID_FIELD = "_id";
    private static final String API_NAME_FIELD = "apiName";
    
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final MetadataValidator metadataValidator;
    
    @Autowired
    public MongoDbMetadataRepository(MongoTemplate mongoTemplate, MetadataValidator metadataValidator) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = new ObjectMapper();
        this.metadataValidator = metadataValidator;
    }
    
    @Override
    public EntityMetadata save(EntityMetadata entityMetadata) {
        // 验证元数据
        ValidationResult validationResult = metadataValidator.validateEntityMetadata(entityMetadata);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("Invalid entity metadata: " + validationResult.getErrorMessage());
        }
        
        // 设置_id为主键，与apiName保持一致
        entityMetadata.setId(entityMetadata.getApiName());
        
        // 保存到MongoDB
        return mongoTemplate.save(entityMetadata, COLLECTION_NAME);
    }
    
    @Override
    public Map<String, ValidationResult> saveAll(List<EntityMetadata> entityMetadataList) {
        Map<String, ValidationResult> resultMap = new HashMap<>();
        List<EntityMetadata> validMetadataList = new ArrayList<>();
        
        for (EntityMetadata metadata : entityMetadataList) {
            ValidationResult validationResult = metadataValidator.validateEntityMetadata(metadata);
            resultMap.put(metadata.getApiName(), validationResult);
            
            if (validationResult.isValid()) {
                metadata.setId(metadata.getApiName());
                validMetadataList.add(metadata);
            }
        }
        
        // 批量保存有效元数据
        if (!validMetadataList.isEmpty()) {
            mongoTemplate.insertAll(validMetadataList);
        }
        
        return resultMap;
    }
    
    @Override
    public Optional<EntityMetadata> findByApiName(String apiName) {
        Query query = new Query(Criteria.where(ID_FIELD).is(apiName));
        EntityMetadata metadata = mongoTemplate.findOne(query, EntityMetadata.class, COLLECTION_NAME);
        return Optional.ofNullable(metadata);
    }
    
    @Override
    public EntityMetadata getByApiName(String apiName) {
        return findByApiName(apiName)
                .orElseThrow(() -> new NoSuchElementException("Entity metadata not found: " + apiName));
    }
    
    @Override
    public List<EntityMetadata> findAll() {
        return mongoTemplate.findAll(EntityMetadata.class, COLLECTION_NAME);
    }
    
    @Override
    public List<EntityMetadata> findByCriteria(MetadataCriteria criteria) {
        Query query = new Query();
        
        if (criteria instanceof DefaultMetadataCriteria) {
            DefaultMetadataCriteria defaultCriteria = (DefaultMetadataCriteria) criteria;
            
            // 应用过滤条件
            if (defaultCriteria.getApiNameLike() != null) {
                query.addCriteria(Criteria.where(API_NAME_FIELD).regex(".*" + defaultCriteria.getApiNameLike() + ".*", "i"));
            }
            
            if (defaultCriteria.getDomain() != null) {
                query.addCriteria(Criteria.where("domain").is(defaultCriteria.getDomain()));
            }
            
            if (defaultCriteria.getEntityType() != null) {
                query.addCriteria(Criteria.where("entityType").is(defaultCriteria.getEntityType()));
            }
            
            if (defaultCriteria.getContainsField() != null) {
                query.addCriteria(Criteria.where("fields.name").is(defaultCriteria.getContainsField()));
            }
            
            if (defaultCriteria.getContainsFieldType() != null) {
                query.addCriteria(Criteria.where("fields.type").is(defaultCriteria.getContainsFieldType()));
            }
            
            if (defaultCriteria.getContainsRelationship() != null) {
                query.addCriteria(Criteria.where("relationships.name").is(defaultCriteria.getContainsRelationship()));
            }
            
            if (defaultCriteria.getContainsTargetEntity() != null) {
                query.addCriteria(Criteria.where("relationships.targetEntity").is(defaultCriteria.getContainsTargetEntity()));
            }
            
            if (defaultCriteria.getAiEnhancementEnabled() != null) {
                if (defaultCriteria.getAiEnhancementEnabled()) {
                    query.addCriteria(Criteria.where("aiEnhancement.enabled").is(true));
                } else {
                    query.addCriteria(new Criteria().orOperator(
                            Criteria.where("aiEnhancement.enabled").is(false),
                            Criteria.where("aiEnhancement").exists(false)
                    ));
                }
            }
            
            if (defaultCriteria.getKnowledgeGraphEnabled() != null) {
                if (defaultCriteria.getKnowledgeGraphEnabled()) {
                    query.addCriteria(Criteria.where("knowledgeGraphConfig.enabled").is(true));
                } else {
                    query.addCriteria(new Criteria().orOperator(
                            Criteria.where("knowledgeGraphConfig.enabled").is(false),
                            Criteria.where("knowledgeGraphConfig").exists(false)
                    ));
                }
            }
            
            if (defaultCriteria.getMcpInterfaceEnabled() != null) {
                if (defaultCriteria.getMcpInterfaceEnabled()) {
                    query.addCriteria(Criteria.where("mcpInterface.enabled").is(true));
                } else {
                    query.addCriteria(new Criteria().orOperator(
                            Criteria.where("mcpInterface.enabled").is(false),
                            Criteria.where("mcpInterface").exists(false)
                    ));
                }
            }
            
            // 应用排序
            if (defaultCriteria.getSortField() != null && defaultCriteria.getSortDirection() != null) {
                Sort.Direction direction = defaultCriteria.getSortDirection() == SortDirection.DESC ? 
                        Sort.Direction.DESC : Sort.Direction.ASC;
                query.with(Sort.by(direction, defaultCriteria.getSortField()));
            }
            
            // 应用分页
            if (defaultCriteria.getPage() != null && defaultCriteria.getSize() != null) {
                query.with(PageRequest.of(defaultCriteria.getPage(), defaultCriteria.getSize()));
            }
        }
        
        return mongoTemplate.find(query, EntityMetadata.class, COLLECTION_NAME);
    }
    
    @Override
    public List<EntityMetadata> findByDomain(String domain) {
        Query query = new Query(Criteria.where("domain").is(domain));
        return mongoTemplate.find(query, EntityMetadata.class, COLLECTION_NAME);
    }
    
    @Override
    public List<EntityMetadata> findByEntityType(String entityType) {
        Query query = new Query(Criteria.where("entityType").is(entityType));
        return mongoTemplate.find(query, EntityMetadata.class, COLLECTION_NAME);
    }
    
    @Override
    public List<EntityMetadata> findByFieldType(String fieldType) {
        Query query = new Query(Criteria.where("fields.type").is(fieldType));
        return mongoTemplate.find(query, EntityMetadata.class, COLLECTION_NAME);
    }
    
    @Override
    public List<EntityMetadata> findByRelatedEntity(String targetEntityApiName) {
        Query query = new Query(Criteria.where("relationships.targetEntity").is(targetEntityApiName));
        return mongoTemplate.find(query, EntityMetadata.class, COLLECTION_NAME);
    }
    
    @Override
    public EntityMetadata update(EntityMetadata entityMetadata) {
        // 验证元数据
        ValidationResult validationResult = metadataValidator.validateEntityMetadata(entityMetadata);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException("Invalid entity metadata: " + validationResult.getErrorMessage());
        }
        
        // 检查是否存在
        Query query = new Query(Criteria.where(ID_FIELD).is(entityMetadata.getApiName()));
        if (mongoTemplate.count(query, EntityMetadata.class, COLLECTION_NAME) == 0) {
            throw new NoSuchElementException("Entity metadata not found: " + entityMetadata.getApiName());
        }
        
        // 设置_id为主键
        entityMetadata.setId(entityMetadata.getApiName());
        
        // 更新MongoDB
        return mongoTemplate.save(entityMetadata, COLLECTION_NAME);
    }
    
    @Override
    public boolean deleteByApiName(String apiName) {
        Query query = new Query(Criteria.where(ID_FIELD).is(apiName));
        return mongoTemplate.remove(query, EntityMetadata.class, COLLECTION_NAME).getDeletedCount() > 0;
    }
    
    @Override
    public int deleteByApiNames(List<String> apiNames) {
        Query query = new Query(Criteria.where(ID_FIELD).in(apiNames));
        return (int) mongoTemplate.remove(query, EntityMetadata.class, COLLECTION_NAME).getDeletedCount();
    }
    
    @Override
    public boolean existsByApiName(String apiName) {
        Query query = new Query(Criteria.where(ID_FIELD).is(apiName));
        return mongoTemplate.exists(query, EntityMetadata.class, COLLECTION_NAME);
    }
    
    @Override
    public long count() {
        return mongoTemplate.count(new Query(), EntityMetadata.class, COLLECTION_NAME);
    }
    
    @Override
    public long countByDomain(String domain) {
        Query query = new Query(Criteria.where("domain").is(domain));
        return mongoTemplate.count(query, EntityMetadata.class, COLLECTION_NAME);
    }
    
    @Override
    public void refreshCache() {
        // MongoDB直接访问，缓存由MongoDB驱动管理
        System.out.println("MongoDB metadata cache refreshed");
    }
    
    @Override
    public void clearCache(String apiName) {
        // MongoDB直接访问，无需清除缓存
        System.out.println("Cache cleared for MongoDB entity: " + apiName);
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
        List<EntityMetadata> allMetadata = findAll();
        List<String> jsonList = new ArrayList<>(allMetadata.size());
        
        for (EntityMetadata metadata : allMetadata) {
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
        List<EntityMetadata> metadataList = new ArrayList<>(jsonList.size());
        
        for (String json : jsonList) {
            try {
                EntityMetadata metadata = objectMapper.readValue(json, EntityMetadata.class);
                metadataList.add(metadata);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to import metadata from JSON: " + json, e);
            }
        }
        
        return saveAll(metadataList);
    }
    
    @Override
    public ConsistencyCheckResult checkConsistency() {
        List<EntityMetadata> allMetadata = findAll();
        List<Inconsistency> inconsistencies = new ArrayList<>();
        Set<String> entityApiNames = allMetadata.stream()
                .map(EntityMetadata::getApiName)
                .collect(Collectors.toSet());
        
        // 检查每个实体元数据
        for (EntityMetadata metadata : allMetadata) {
            // 检查主键
            boolean hasPrimaryKey = metadata.getFields().stream().anyMatch(f -> f.isPrimaryKey());
            if (!hasPrimaryKey) {
                inconsistencies.add(new DefaultInconsistency(
                        InconsistencyType.MISSING_PRIMARY_KEY,
                        metadata.getApiName(),
                        "Entity does not have a primary key field"
                ));
            }
            
            // 检查关系目标实体
            for (var relationship : metadata.getRelationships()) {
                if (!entityApiNames.contains(relationship.getTargetEntity())) {
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
                        "Invalid API name format"
                ));
            }
            
            // 检查字段名称格式
            for (var field : metadata.getFields()) {
                if (!field.getName().matches("^[a-z][a-z0-9_]*$")) {
                    inconsistencies.add(new DefaultInconsistency(
                            InconsistencyType.INVALID_FIELD_NAME_FORMAT,
                            metadata.getApiName(),
                            "Invalid field name format: " + field.getName()
                    ));
                }
            }
        }
        
        // 检查循环依赖
        for (EntityMetadata metadata : allMetadata) {
            Set<String> visited = new HashSet<>();
            if (hasCircularDependency(metadata.getApiName(), visited, entityApiNames, allMetadata)) {
                inconsistencies.add(new DefaultInconsistency(
                        InconsistencyType.CIRCULAR_RELATIONSHIP,
                        metadata.getApiName(),
                        "Circular relationship detected"
                ));
            }
        }
        
        return new DefaultConsistencyCheckResult(inconsistencies.isEmpty(), inconsistencies);
    }
    
    private boolean hasCircularDependency(String currentEntity, Set<String> visited, 
                                        Set<String> allEntities, List<EntityMetadata> allMetadata) {
        if (visited.contains(currentEntity)) {
            return true; // 发现循环依赖
        }
        
        visited.add(currentEntity);
        
        // 查找当前实体的所有关系
        Optional<EntityMetadata> currentMetadataOpt = allMetadata.stream()
                .filter(m -> m.getApiName().equals(currentEntity))
                .findFirst();
        
        if (currentMetadataOpt.isPresent()) {
            EntityMetadata currentMetadata = currentMetadataOpt.get();
            for (var relationship : currentMetadata.getRelationships()) {
                String targetEntity = relationship.getTargetEntity();
                if (allEntities.contains(targetEntity)) {
                    if (hasCircularDependency(targetEntity, new HashSet<>(visited), allEntities, allMetadata)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    @Override
    public ConsistencyCheckResult repairConsistency() {
        ConsistencyCheckResult result = checkConsistency();
        List<Inconsistency> inconsistencies = result.getInconsistencies();
        
        // 尝试修复不一致问题
        for (Inconsistency inconsistency : inconsistencies) {
            if (inconsistency instanceof DefaultInconsistency) {
                DefaultInconsistency defaultInconsistency = (DefaultInconsistency) inconsistency;
                
                try {
                    switch (inconsistency.getType()) {
                        case MISSING_PRIMARY_KEY:
                            // 尝试添加默认ID字段
                            EntityMetadata metadata = getByApiName(inconsistency.getEntityApiName());
                            // 这里应该有具体的修复逻辑
                            defaultInconsistency.setFixMessage("Manual intervention required to add primary key");
                            break;
                        case INVALID_API_NAME_FORMAT:
                        case INVALID_FIELD_NAME_FORMAT:
                            defaultInconsistency.setFixMessage("Naming conventions violation - manual correction needed");
                            break;
                        default:
                            defaultInconsistency.setFixMessage("Manual repair required");
                            break;
                    }
                } catch (Exception e) {
                    defaultInconsistency.setFixMessage("Failed to repair: " + e.getMessage());
                }
            }
        }
        
        return new DefaultConsistencyCheckResult(false, inconsistencies); // 即使部分修复，仍然返回需要人工干预
    }
}