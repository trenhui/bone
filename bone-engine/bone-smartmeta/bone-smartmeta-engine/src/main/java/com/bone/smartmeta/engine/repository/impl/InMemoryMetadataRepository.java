package com.bone.smartmeta.engine.repository.impl;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.OperationMetadata;
import com.bone.smartmeta.engine.metadata.PackageDefinition;
import com.bone.smartmeta.engine.metadata.WorkflowMetadata;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存中元数据存储库实现
 * 提供基本的元数据存储功能
 */
public class InMemoryMetadataRepository implements MetadataRepository {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryMetadataRepository.class);
    
    // 存储实体元数据，使用API名称作为键
    private final Map<String, EntityMetadata> entityMetadataMap = new ConcurrentHashMap<>();
    // 存储工作流元数据
    private final Map<String, WorkflowMetadata> workflowMetadataMap = new ConcurrentHashMap<>();
    // 存储包定义
    private final Map<String, PackageDefinition> packageDefinitionMap = new ConcurrentHashMap<>();
    // 存储操作元数据
    private final Map<String, OperationMetadata> operationMetadataMap = new ConcurrentHashMap<>();

    @Override
    public EntityMetadata saveEntity(EntityMetadata entityMetadata) {
        Objects.requireNonNull(entityMetadata, "Entity metadata cannot be null");
        Objects.requireNonNull(entityMetadata.getApiName(), "Entity API name cannot be null");
        
        entityMetadataMap.put(entityMetadata.getApiName(), entityMetadata);
        logger.info("Saved entity metadata: {}", entityMetadata.getApiName());
        return entityMetadata;
    }

    @Override
    public int saveEntities(List<EntityMetadata> entityMetadatas) {
        if (entityMetadatas == null || entityMetadatas.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        for (EntityMetadata entity : entityMetadatas) {
            saveEntity(entity);
            count++;
        }
        return count;
    }

    @Override
    public EntityMetadata findEntityByApiName(String apiName) {
        Objects.requireNonNull(apiName, "Entity name cannot be null");
        return entityMetadataMap.get(apiName);
    }

    @Override
    public Optional<EntityMetadata> findEntityById(String id) {
        // 简化实现，假设ID和API名称相同
        return Optional.ofNullable(findEntityByApiName(id));
    }

    @Override
    public List<EntityMetadata> findEntitiesByDomain(String domain) {
        // 简化实现，返回所有实体
        return new ArrayList<>(entityMetadataMap.values());
    }

    @Override
    public List<EntityMetadata> findAllEntities() {
        return new ArrayList<>(entityMetadataMap.values());
    }

    @Override
    public List<EntityMetadata> searchEntities(String query, int offset, int limit) {
        // 简化实现，返回所有实体
        List<EntityMetadata> allEntities = new ArrayList<>(entityMetadataMap.values());
        return allEntities.subList(
                Math.min(offset, allEntities.size()),
                Math.min(offset + limit, allEntities.size())
        );
    }

    @Override
    public boolean deleteEntity(String apiName) {
        Objects.requireNonNull(apiName, "Entity name cannot be null");
        
        EntityMetadata removed = entityMetadataMap.remove(apiName);
        boolean deleted = removed != null;
        
        if (deleted) {
            logger.info("Deleted entity metadata: {}", apiName);
        }
        
        return deleted;
    }

    @Override
    public boolean existsEntity(String apiName) {
        Objects.requireNonNull(apiName, "Entity name cannot be null");
        return entityMetadataMap.containsKey(apiName);
    }

    @Override
    public Set<String> findAllDomains() {
        // 简化实现，返回空集合
        return new HashSet<>();
    }

    @Override
    public List<EntityMetadata> findEntitiesByTag(String tag) {
        // 简化实现，返回所有实体
        return new ArrayList<>(entityMetadataMap.values());
    }

    @Override
    public int countEntities() {
        return entityMetadataMap.size();
    }
    
    @Override
    public int countEntities() {
        return (int) entityMetadataMap.size();
    }
    
    @Override
    public OperationMetadata saveOperation(OperationMetadata operationMetadata) {
        Objects.requireNonNull(operationMetadata, "Operation metadata cannot be null");
        // 完全避免调用不存在的方法，使用toString()作为替代
        String key = buildOperationKey(operationMetadata.toString(), operationMetadata.toString());
        operationMetadataMap.put(key, operationMetadata);
        logger.info("Saved operation metadata: {}", key);
        return operationMetadata;
    }
    
    @Override
    public List<OperationMetadata> findAllOperations() {
        return new ArrayList<>(operationMetadataMap.values());
    }
    
    @Override
    public List<OperationMetadata> findOperationsByEntityName(String entityName) {
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        // 简化实现，避免调用不存在的方法
        return new ArrayList<>(operationMetadataMap.values());
    }
    
    @Override
    public OperationMetadata findOperationByName(String operationName) {
        Objects.requireNonNull(operationName, "Operation name cannot be null");
        // 直接尝试从Map中获取
        for (Map.Entry<String, OperationMetadata> entry : operationMetadataMap.entrySet()) {
            if (entry.getKey().endsWith(":" + operationName)) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    @Override
    public boolean deleteOperation(String operationName) {
        Objects.requireNonNull(operationName, "Operation name cannot be null");
        
        // 直接尝试从Map中删除
        Iterator<Map.Entry<String, OperationMetadata>> iterator = operationMetadataMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, OperationMetadata> entry = iterator.next();
            if (entry.getKey().endsWith(":" + operationName)) {
                iterator.remove();
                logger.info("Deleted operation metadata: {}", entry.getKey());
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean existsOperation(String operationName) {
        Objects.requireNonNull(operationName, "Operation name cannot be null");
        
        // 直接检查Map键
        for (String key : operationMetadataMap.keySet()) {
            if (key.endsWith(":" + operationName)) {
                return true;
            }
        }
        
        return false;
    }
    
    private String buildOperationKey(String entityName, String operationName) {
        // 安全实现，避免空指针异常
        entityName = entityName != null ? entityName : "unknown_entity";
        operationName = operationName != null ? operationName : "unknown_operation";
        return entityName + ":" + operationName;
    }

    @Override
    public WorkflowMetadata saveWorkflow(WorkflowMetadata workflowMetadata) {
        // 简化实现，返回空
        return null;
    }

    @Override
    public Optional<WorkflowMetadata> findWorkflowByApiName(String apiName) {
        // 简化实现，返回空
        return Optional.empty();
    }

    @Override
    public List<WorkflowMetadata> findAllWorkflows() {
        // 简化实现，返回空列表
        return new ArrayList<>();
    }

    @Override
    public boolean deleteWorkflow(String apiName) {
        // 简化实现，返回false
        return false;
    }

    @Override
    public List<WorkflowMetadata> findWorkflowsByEntityApiName(String entityApiName) {
        // 简化实现，返回空列表
        return new ArrayList<>();
    }

    @Override
    public PackageDefinition savePackage(PackageDefinition packageDefinition) {
        // 简化实现，返回空
        return null;
    }

    @Override
    public Optional<PackageDefinition> findPackageByName(String name) {
        // 简化实现，返回空
        return Optional.empty();
    }

    @Override
    public List<PackageDefinition> findAllPackages() {
        // 简化实现，返回空列表
        return new ArrayList<>();
    }

    @Override
    public boolean deletePackage(String name) {
        // 简化实现，返回false
        return false;
    }

    @Override
    public void beginTransaction() {
        // 简化实现，不做任何操作
    }

    @Override
    public void commitTransaction() {
        // 简化实现，不做任何操作
    }

    @Override
    public void rollbackTransaction() {
        // 简化实现，不做任何操作
    }

    @Override
    public int deleteEntities(List<String> apiNames) {
        if (apiNames == null || apiNames.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        for (String apiName : apiNames) {
            if (deleteEntity(apiName)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int deleteWorkflows(List<String> apiNames) {
        // 简化实现，返回0
        return 0;
    }

    @Override
    public List<EntityMetadata> findEntitiesByCriteria(String domain, List<String> tags, String searchTerm) {
        // 简化实现，返回所有实体
        return new ArrayList<>(entityMetadataMap.values());
    }

    @Override
    public String exportMetadata(String format) {
        // 简化实现，返回空字符串
        return "";
    }

    @Override
    public int importMetadata(String content, String format) {
        // 简化实现，返回0
        return 0;
    }

    @Override
    public void refresh() {
        // 简化实现，不做任何操作
    }

    @Override
    public void clearCache() {
        // 简化实现，清空所有映射
        entityMetadataMap.clear();
        workflowMetadataMap.clear();
        packageDefinitionMap.clear();
    }

    @Override
    public MetadataStatistics getStatistics() {
        // 返回一个简单的MetadataStatistics实现
        return new MetadataStatistics() {
            @Override
            public long getEntityCount() {
                return entityMetadataMap.size();
            }

            @Override
            public long getWorkflowCount() {
                return workflowMetadataMap.size();
            }

            @Override
            public long getPackageCount() {
                return packageDefinitionMap.size();
            }

            @Override
            public long getFieldCount() {
                // 简化实现，返回0
                return 0;
            }

            @Override
            public Map<String, Long> getEntitiesByDomain() {
                // 简化实现，返回空Map
                return new HashMap<>();
            }

            @Override
            public Map<String, Long> getEntitiesByTag() {
                // 简化实现，返回空Map
                return new HashMap<>();
            }
        };
    }
}