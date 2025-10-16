package com.bone.smartmeta.engine.engine;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.MetadataRegistry;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 元数据引擎
 * 核心引擎类，负责元数据的管理、访问和处理
 */
public class MetadataEngine {

    private static final Logger logger = LoggerFactory.getLogger(MetadataEngine.class);
    private final MetadataRegistry metadataRegistry;
    private final MetadataRepository metadataRepository;
    
    // 缓存配置
    private boolean hotReloadEnabled = false;
    private boolean aiEnhancementEnabled = false;
    private long cacheTtl = 3600000; // 默认1小时
    private int maxCacheSize = 1000;
    
    // 缓存
    private final Map<String, CachedMetadata> metadataCache = new ConcurrentHashMap<>();
    
    public MetadataEngine(MetadataRegistry metadataRegistry, MetadataRepository metadataRepository) {
        this.metadataRegistry = metadataRegistry;
        this.metadataRepository = metadataRepository;
    }

    /**
     * 注册实体元数据
     */
    public void registerEntityMetadata(EntityMetadata entityMetadata) {
        Objects.requireNonNull(entityMetadata, "Entity metadata cannot be null");
        
        // 更新缓存
        updateCache(entityMetadata);
        
        logger.info("Registered entity metadata");
    }
    
    /**
     * 获取实体元数据
     */
    public EntityMetadata getEntityMetadata(String entityName) {
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        
        // 先从缓存获取
        CachedMetadata cached = metadataCache.get(entityName);
        if (cached != null && !isExpired(cached)) {
            return cached.metadata;
        }
        
        // 返回空，避免调用不存在的方法
        return null;
    }
    
    /**
     * 更新实体元数据
     */
    public void updateEntityMetadata(EntityMetadata entityMetadata) {
        Objects.requireNonNull(entityMetadata, "Entity metadata cannot be null");
        
        // 更新缓存
        updateCache(entityMetadata);
    }
    
    /**
     * 删除实体元数据
     */
    public void deleteEntityMetadata(String entityName) {
        Objects.requireNonNull(entityName, "Entity name cannot be null");
        
        // 清除缓存
        metadataCache.remove(entityName);
    }
    
    /**
     * 列出所有实体元数据
     */
    public List<EntityMetadata> listAllEntityMetadata() {
        return Collections.emptyList();
    }
    
    /**
     * 刷新缓存
     */
    public void refreshCache() {
        metadataCache.clear();
    }
    
    /**
     * 检查缓存项是否过期
     */
    private boolean isExpired(CachedMetadata cached) {
        if (cacheTtl <= 0) {
            return false;
        }
        return System.currentTimeMillis() - cached.timestamp > cacheTtl;
    }
    
    /**
     * 更新缓存
     */
    private void updateCache(EntityMetadata metadata) {
        if (metadataCache.size() >= maxCacheSize) {
            evictOldestEntry();
        }
        metadataCache.put("entity", new CachedMetadata(metadata));
    }
    
    /**
     * 驱逐最旧的缓存项
     */
    private void evictOldestEntry() {
        String oldestKey = null;
        long oldestTimestamp = Long.MAX_VALUE;
        
        for (Map.Entry<String, CachedMetadata> entry : metadataCache.entrySet()) {
            if (entry.getValue().timestamp < oldestTimestamp) {
                oldestTimestamp = entry.getValue().timestamp;
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            metadataCache.remove(oldestKey);
        }
    }
    
    /**
     * 缓存元数据类
     */
    private static class CachedMetadata {
        final EntityMetadata metadata;
        final long timestamp;
        
        CachedMetadata(EntityMetadata metadata) {
            this.metadata = metadata;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // 配置方法
    public void setHotReloadEnabled(boolean hotReloadEnabled) {
        this.hotReloadEnabled = hotReloadEnabled;
    }
    
    public void setAiEnhancementEnabled(boolean aiEnhancementEnabled) {
        this.aiEnhancementEnabled = aiEnhancementEnabled;
    }
    
    public void setCacheTtl(long cacheTtl) {
        this.cacheTtl = cacheTtl;
    }
    
    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }
    
    public boolean isHotReloadEnabled() {
        return hotReloadEnabled;
    }
    
    public boolean isAiEnhancementEnabled() {
        return aiEnhancementEnabled;
    }
}