package com.bone.smartmeta.engine.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 元数据注册表默认实现类
 * 提供实体元数据的完整管理功能
 */
public class DefaultMetadataRegistry implements MetadataRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultMetadataRegistry.class);
    
    // 存储实体元数据的主映射（API名称 -> 实体元数据）
    private final Map<String, EntityMetadata> entityMetadataMap = new ConcurrentHashMap<>();
    
    // 实体类型索引
    private final Map<String, List<EntityMetadata>> entityTypeIndex = new ConcurrentHashMap<>();
    
    // 领域索引
    private final Map<String, List<EntityMetadata>> domainIndex = new ConcurrentHashMap<>();
    
    // 标签索引
    private final Map<String, List<EntityMetadata>> tagIndex = new ConcurrentHashMap<>();
    
    // 元数据变更监听器列表
    private final List<MetadataChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    @Override
    public synchronized boolean registerEntity(EntityMetadata entityMetadata) {
        if (entityMetadata == null || entityMetadata.getApiName() == null) {
            log.warn("Invalid entity metadata: null or missing API name");
            return false;
        }
        
        String entityApiName = entityMetadata.getApiName();
        boolean isUpdate = entityMetadataMap.containsKey(entityApiName);
        
        // 存储实体元数据
        entityMetadataMap.put(entityApiName, entityMetadata);
        
        // 更新索引
        updateIndexes(entityMetadata, isUpdate);
        
        // 触发事件通知
        if (isUpdate) {
            notifyEntityUpdated(entityMetadata);
        } else {
            notifyEntityRegistered(entityMetadata);
        }
        
        log.info("Entity metadata {}: {}", isUpdate ? "updated" : "registered", entityApiName);
        return true;
    }
    
    @Override
    public synchronized int registerEntities(List<EntityMetadata> entityMetadataList) {
        if (entityMetadataList == null || entityMetadataList.isEmpty()) {
            return 0;
        }
        
        int registeredCount = 0;
        for (EntityMetadata entityMetadata : entityMetadataList) {
            if (registerEntity(entityMetadata)) {
                registeredCount++;
            }
        }
        
        return registeredCount;
    }
    
    @Override
    public synchronized boolean updateEntity(EntityMetadata entityMetadata) {
        if (entityMetadata == null || entityMetadata.getApiName() == null) {
            log.warn("Invalid entity metadata for update: null or missing API name");
            return false;
        }
        
        String entityApiName = entityMetadata.getApiName();
        if (!entityMetadataMap.containsKey(entityApiName)) {
            log.warn("Cannot update non-existent entity: {}", entityApiName);
            return false;
        }
        
        return registerEntity(entityMetadata); // 复用注册逻辑
    }
    
    @Override
    public synchronized boolean unregisterEntity(String entityApiName) {
        if (entityApiName == null) {
            log.warn("Cannot unregister entity with null API name");
            return false;
        }
        
        EntityMetadata removed = entityMetadataMap.remove(entityApiName);
        if (removed == null) {
            log.warn("Entity not found for unregistration: {}", entityApiName);
            return false;
        }
        
        // 从索引中移除
        removeFromIndexes(removed);
        
        // 触发事件通知
        notifyEntityUnregistered(entityApiName);
        
        log.info("Entity unregistered: {}", entityApiName);
        return true;
    }
    
    @Override
    public EntityMetadata getEntityMetadata(String entityApiName) {
        if (entityApiName == null) {
            return null;
        }
        return entityMetadataMap.get(entityApiName);
    }
    
    @Override
    public Optional<EntityMetadata> getEntityMetadataOptional(String entityApiName) {
        return Optional.ofNullable(getEntityMetadata(entityApiName));
    }
    
    @Override
    public boolean isEntityRegistered(String entityApiName) {
        return entityApiName != null && entityMetadataMap.containsKey(entityApiName);
    }
    
    @Override
    public Collection<EntityMetadata> getAllEntityMetadata() {
        return new ArrayList<>(entityMetadataMap.values());
    }
    
    @Override
    public Set<String> getAllEntityApiNames() {
        return new HashSet<>(entityMetadataMap.keySet());
    }
    
    @Override
    public List<EntityMetadata> getEntityMetadataByType(String entityType) {
        if (entityType == null) {
            return Collections.emptyList();
        }
        return entityTypeIndex.getOrDefault(entityType, Collections.emptyList());
    }
    
    @Override
    public List<EntityMetadata> getEntityMetadataByDomain(String domain) {
        if (domain == null) {
            return Collections.emptyList();
        }
        return domainIndex.getOrDefault(domain, Collections.emptyList());
    }
    
    @Override
    public List<EntityMetadata> getEntityMetadataByTag(String tag) {
        if (tag == null) {
            return Collections.emptyList();
        }
        return tagIndex.getOrDefault(tag, Collections.emptyList());
    }
    
    @Override
    public List<EntityMetadata> searchEntityMetadata(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String lowerQuery = query.toLowerCase().trim();
        return entityMetadataMap.values().stream()
                .filter(entity -> 
                    (entity.getName() != null && entity.getName().toLowerCase().contains(lowerQuery)) ||
                    (entity.getApiName() != null && entity.getApiName().toLowerCase().contains(lowerQuery)) ||
                    (entity.getDescription() != null && entity.getDescription().toLowerCase().contains(lowerQuery)) ||
                    (entity.getDomain() != null && entity.getDomain().toLowerCase().contains(lowerQuery))
                )
                .collect(Collectors.toList());
    }
    
    @Override
    public synchronized void clear() {
        Set<String> entityApiNames = new HashSet<>(entityMetadataMap.keySet());
        
        // 清空所有数据
        entityMetadataMap.clear();
        entityTypeIndex.clear();
        domainIndex.clear();
        tagIndex.clear();
        
        // 触发注销事件
        entityApiNames.forEach(this::notifyEntityUnregistered);
        
        log.info("Metadata registry cleared");
    }
    
    @Override
    public int size() {
        return entityMetadataMap.size();
    }
    
    @Override
    public boolean isEmpty() {
        return entityMetadataMap.isEmpty();
    }
    
    @Override
    public synchronized void refreshCache() {
        // 重新构建索引
        entityTypeIndex.clear();
        domainIndex.clear();
        tagIndex.clear();
        
        for (EntityMetadata entityMetadata : entityMetadataMap.values()) {
            buildIndexes(entityMetadata);
        }
        
        log.info("Metadata registry cache refreshed");
    }
    
    @Override
    public synchronized byte[] exportRegistry() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            
            // 只导出实体元数据集合
            List<EntityMetadata> metadataList = new ArrayList<>(entityMetadataMap.values());
            oos.writeObject(metadataList);
            oos.flush();
            
            log.info("Exported {} entities from metadata registry", metadataList.size());
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to export metadata registry", e);
            return new byte[0];
        }
    }
    
    @Override
    public synchronized int importRegistry(byte[] registryData) {
        if (registryData == null || registryData.length == 0) {
            log.warn("Empty registry data to import");
            return 0;
        }
        
        try (ByteArrayInputStream bais = new ByteArrayInputStream(registryData);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            
            @SuppressWarnings("unchecked")
            List<EntityMetadata> metadataList = (List<EntityMetadata>) ois.readObject();
            
            int importedCount = registerEntities(metadataList);
            log.info("Imported {} entities into metadata registry", importedCount);
            return importedCount;
        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to import metadata registry", e);
            return 0;
        }
    }
    
    @Override
    public FieldMetadata getFieldMetadata(String entityApiName, String fieldApiName) {
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        if (entityMetadata == null || entityMetadata.getFields() == null) {
            return null;
        }
        
        return entityMetadata.getFields().entrySet().stream()
                .filter(entry -> fieldApiName.equals(entry.getValue().getApiName()))
                .findFirst()
                  .map(Map.Entry::getValue)
                  .orElse(null);
    }
    
    @Override
    public List<FieldMetadata> getAllFieldMetadata(String entityApiName) {
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        if (entityMetadata == null || entityMetadata.getFields() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(entityMetadata.getFields().values());
    }
    
    @Override
    public List<FieldMetadata> getCalculatedFieldMetadata(String entityApiName) {
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        if (entityMetadata == null || entityMetadata.getFields() == null) {
            return Collections.emptyList();
        }
        
        return entityMetadata.getFields().entrySet().stream()
                .filter(entry -> entry.getValue().isCalculated())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<FieldMetadata> getVirtualFieldMetadata(String entityApiName) {
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        if (entityMetadata == null || entityMetadata.getFields() == null) {
            return Collections.emptyList();
        }
        
        return entityMetadata.getFields().entrySet().stream()
                .filter(entry -> entry.getValue().isVirtual())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
    
    @Override
    public RelationshipMetadata getRelationshipMetadata(String entityApiName, String relationshipName) {
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        if (entityMetadata == null || entityMetadata.getRelationships() == null) {
            return null;
        }
        
        return entityMetadata.getRelationships().stream()
                .filter(rel -> relationshipName.equals(rel.getName()) || relationshipName.equals(rel.getApiName()))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public List<RelationshipMetadata> getAllRelationshipMetadata(String entityApiName) {
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        if (entityMetadata == null || entityMetadata.getRelationships() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(entityMetadata.getRelationships());
    }
    
    @Override
    public List<BusinessRuleMetadata> getBusinessRuleMetadata(String entityApiName) {
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        if (entityMetadata == null || entityMetadata.getBusinessRules() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(entityMetadata.getBusinessRules());
    }
    
    @Override
    public void addMetadataChangeListener(MetadataChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            log.info("Metadata change listener added: {}", listener.getClass().getSimpleName());
        }
    }
    
    @Override
    public void removeMetadataChangeListener(MetadataChangeListener listener) {
        if (listener != null && listeners.remove(listener)) {
            log.info("Metadata change listener removed: {}", listener.getClass().getSimpleName());
        }
    }
    
    // 更新索引
    private void updateIndexes(EntityMetadata entityMetadata, boolean isUpdate) {
        if (isUpdate) {
            // 更新时先移除旧索引
            removeFromIndexes(entityMetadata);
        }
        // 构建新索引
        buildIndexes(entityMetadata);
    }
    
    // 构建索引
    private void buildIndexes(EntityMetadata entityMetadata) {
        // 构建实体类型索引
        if (entityMetadata.getEntityType() != null) {
            entityTypeIndex.computeIfAbsent(entityMetadata.getEntityType(), 
                                           k -> new CopyOnWriteArrayList<>()).add(entityMetadata);
        }
        
        // 构建领域索引
        if (entityMetadata.getDomain() != null) {
            domainIndex.computeIfAbsent(entityMetadata.getDomain(), 
                                       k -> new CopyOnWriteArrayList<>()).add(entityMetadata);
        }
        
        // 构建标签索引
        if (entityMetadata.getTags() != null) {
            // 假设Tags是Collection类型，正确遍历
            for (Map.Entry<String, String> tagEntry : entityMetadata.getTags().entrySet()) {
                if (tagEntry != null && tagEntry.getKey() != null) {
                    String tag = tagEntry.getKey();
                    tagIndex.computeIfAbsent(tag, 
                                           k -> new CopyOnWriteArrayList<>()).add(entityMetadata);
                }
            }
        }
    }
    
    // 从索引中移除
    private void removeFromIndexes(EntityMetadata entityMetadata) {
        // 从实体类型索引移除
        if (entityMetadata.getEntityType() != null) {
            List<EntityMetadata> typeEntities = entityTypeIndex.get(entityMetadata.getEntityType());
            if (typeEntities != null) {
                typeEntities.remove(entityMetadata);
            }
        }
        
        // 从领域索引移除
        if (entityMetadata.getDomain() != null) {
            List<EntityMetadata> domainEntities = domainIndex.get(entityMetadata.getDomain());
            if (domainEntities != null) {
                domainEntities.remove(entityMetadata);
            }
        }
        
        // 从标签索引移除
        if (entityMetadata.getTags() != null) {
            // 假设Tags是Collection类型，正确遍历
            for (Map.Entry<String, String> tagEntry : entityMetadata.getTags().entrySet()) {
                if (tagEntry != null && tagEntry.getKey() != null) {
                    String tag = tagEntry.getKey();
                    List<EntityMetadata> tagEntities = tagIndex.get(tag);
                    if (tagEntities != null) {
                        tagEntities.remove(entityMetadata);
                    }
                }
            }
        }
    }
    
    // 通知实体注册事件
    private void notifyEntityRegistered(EntityMetadata entityMetadata) {
        listeners.forEach(listener -> {
            try {
                listener.onEntityRegistered(entityMetadata);
            } catch (Exception e) {
                log.error("Error in metadata change listener", e);
            }
        });
    }
    
    // 通知实体更新事件
    private void notifyEntityUpdated(EntityMetadata entityMetadata) {
        listeners.forEach(listener -> {
            try {
                listener.onEntityUpdated(entityMetadata);
            } catch (Exception e) {
                log.error("Error in metadata change listener", e);
            }
        });
    }
    
    // 通知实体注销事件
    private void notifyEntityUnregistered(String entityApiName) {
        listeners.forEach(listener -> {
            try {
                listener.onEntityUnregistered(entityApiName);
            } catch (Exception e) {
                log.error("Error in metadata change listener", e);
            }
        });
    }
    
    // 通知字段更新事件
    private void notifyFieldUpdated(String entityApiName, FieldMetadata fieldMetadata) {
        listeners.forEach(listener -> {
            try {
                listener.onFieldUpdated(entityApiName, fieldMetadata);
            } catch (Exception e) {
                log.error("Error in metadata change listener", e);
            }
        });
    }
    
    // 通知关系更新事件
    private void notifyRelationshipUpdated(String entityApiName, RelationshipMetadata relationshipMetadata) {
        listeners.forEach(listener -> {
            try {
                listener.onRelationshipUpdated(entityApiName, relationshipMetadata);
            } catch (Exception e) {
                log.error("Error in metadata change listener", e);
            }
        });
    }
}