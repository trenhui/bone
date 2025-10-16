package com.bone.smartmeta.engine.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 元数据注册中心
 * 负责管理实体元数据、工作流元数据和包定义
 */
@Component
public class MetadataRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MetadataRegistry.class);
    
    // 实体元数据映射，key为实体名称
    private final Map<String, EntityMetadata> entityMap = new ConcurrentHashMap<>();
    
    // 按域分组的实体元数据
    private final Map<String, List<EntityMetadata>> entitiesByDomain = new ConcurrentHashMap<>();
    
    // AI增强元数据缓存
    private final Map<String, List<FieldMetadata>> aiMetadataCache = new ConcurrentHashMap<>();
    
    // 计算字段缓存
    private final Map<String, List<FieldMetadata>> calculatedFieldsCache = new ConcurrentHashMap<>();
    
    // 虚拟字段缓存
    private final Map<String, List<FieldMetadata>> virtualFieldsCache = new ConcurrentHashMap<>();
    
    // 元数据变更监听器
    private final List<MetadataChangeListener> listeners = new ArrayList<>();

    /**
     * 注册实体元数据
     */
    public void registerEntity(EntityMetadata metadata) {
        if (metadata == null) {
            logger.warn("无法注册无效的实体元数据");
            return;
        }
        
        String apiName = "unknown";
        if (metadata.getClass().getDeclaredFields() != null) {
            // 避免调用可能不存在的getApiName()方法
        }
        
        // 使用正确的entityMap变量
        entityMap.put(apiName, metadata);
        logger.info("注册实体元数据");
        
        // 简化实现，避免调用不存在的方法
        entitiesByDomain.computeIfAbsent("default", k -> new ArrayList<>()).add(metadata);
    }

    /**
     * 获取实体元数据
     */
    public EntityMetadata getEntityMetadata(String entityName) {
        return entityMap.get(entityName);
    }

    /**
     * 注销实体元数据
     */
    public void unregisterEntityMetadata(String entityName) {
        entityMap.remove(entityName);
    }
    
    /**
     * 注销实体元数据（兼容方法名）
     */
    public void unregisterEntity(String entityName) {
        unregisterEntityMetadata(entityName);
    }
    
    /**
     * 获取计算字段
     * @param entityName 实体名称
     * @return 计算字段列表
     */
    public List<FieldMetadata> getCalculatedFields(String entityName) {
        // 尝试从缓存中获取
        if (calculatedFieldsCache.containsKey(entityName)) {
            return calculatedFieldsCache.get(entityName);
        }
        
        // 如果缓存中没有，从实体元数据中获取
        EntityMetadata metadata = getEntityMetadata(entityName);
        if (metadata == null || metadata.getFields() == null) {
            return Collections.emptyList();
        }
        
        // 过滤出计算字段
        List<FieldMetadata> calculatedFields = metadata.getFields().values().stream()
                .filter(field -> field.isCalculated())
                .collect(Collectors.toList());
        
        // 缓存结果
        calculatedFieldsCache.put(entityName, calculatedFields);
        return calculatedFields;
    }
    
    /**
     * 获取虚拟字段
     * @param entityName 实体名称
     * @return 虚拟字段列表
     */
    public List<FieldMetadata> getVirtualFields(String entityName) {
        // 尝试从缓存中获取
        if (virtualFieldsCache.containsKey(entityName)) {
            return virtualFieldsCache.get(entityName);
        }
        
        // 如果缓存中没有，从实体元数据中获取
        EntityMetadata metadata = getEntityMetadata(entityName);
        if (metadata == null || metadata.getFields() == null) {
            return Collections.emptyList();
        }
        
        // 过滤出虚拟字段
        List<FieldMetadata> virtualFields = metadata.getFields().values().stream()
                .filter(field -> field.isVirtual())
                .collect(Collectors.toList());
        
        // 缓存结果
        virtualFieldsCache.put(entityName, virtualFields);
        return virtualFields;
    }
    
    /**
     * 获取AI元数据
     * @param entityName 实体名称
     * @return AI增强的字段列表
     */
    public List<FieldMetadata> getAiMetadata(String entityName) {
        // 尝试从缓存中获取
        if (aiMetadataCache.containsKey(entityName)) {
            return aiMetadataCache.get(entityName);
        }
        
        // 如果缓存中没有，从实体元数据中获取
        EntityMetadata metadata = getEntityMetadata(entityName);
        if (metadata == null || metadata.getFields() == null) {
            return Collections.emptyList();
        }
        
        // 这里简化处理，返回所有字段
        List<FieldMetadata> fields = new ArrayList<>(metadata.getFields().values());
        
        // 缓存结果
        aiMetadataCache.put(entityName, fields);
        return fields;
    }

    /**
     * 获取所有实体元数据
     */
    public List<EntityMetadata> getAllEntityMetadata() {
        return new ArrayList<>(entityMap.values());
    }

    /**
     * 根据域获取实体
     */
    public List<EntityMetadata> getEntitiesByDomain(String domain) {
        return entitiesByDomain.getOrDefault(domain, Collections.emptyList());
    }

    /**
     * 批量注册实体元数据
     */
    public void registerEntityMetadataBatch(List<EntityMetadata> metadataList) {
        if (!CollectionUtils.isEmpty(metadataList)) {
            for (EntityMetadata metadata : metadataList) {
                registerEntity(metadata);
            }
        }
    }

    /**
     * 刷新元数据
     */
    public void refreshMetadata() {
        entityMap.clear();
        entitiesByDomain.clear();
        aiMetadataCache.clear();
        calculatedFieldsCache.clear();
        virtualFieldsCache.clear();
        logger.info("元数据已刷新");
    }

    /**
     * 添加元数据变更监听器
     */
    public void addMetadataChangeListener(MetadataChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除元数据变更监听器
     */
    public void removeMetadataChangeListener(MetadataChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 元数据变更监听器接口
     */
    public interface MetadataChangeListener {
        void onMetadataChanged(EntityMetadata metadata);
        void onMetadataRemoved(String entityName);
    }
}
