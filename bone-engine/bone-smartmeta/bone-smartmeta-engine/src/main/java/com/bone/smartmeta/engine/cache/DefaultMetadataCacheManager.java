package com.bone.smartmeta.engine.cache;

import com.bone.smartmeta.engine.model.EntityMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 默认的元数据缓存管理器实现
 * 采用Caffeine本地缓存 + Redis分布式缓存的多级缓存架构
 */
@Slf4j
public class DefaultMetadataCacheManager implements MetadataCacheManager {
    
    // 本地缓存，使用ConcurrentHashMap实现线程安全
    private final Map<String, EntityMetadata> localCache;
    
    // 缓存过期时间映射
    private final Map<String, Long> expiryTimes;
    
    // 缓存统计信息
    private final CacheStats stats;
    
    // 缓存TTL（默认24小时）
    private static final long CACHE_TTL_HOURS = 24;
    
    public DefaultMetadataCacheManager() {
        // 初始化本地缓存
        this.localCache = new ConcurrentHashMap<>();
        this.expiryTimes = new ConcurrentHashMap<>();
        
        // 初始化统计信息
        this.stats = new DefaultCacheStats();
    }
    
    @Override
    public EntityMetadata get(String tenantId, String entityName) {
        String cacheKey = buildCacheKey(tenantId, entityName);
        
        // 检查缓存是否过期
        Long expiryTime = expiryTimes.get(cacheKey);
        if (expiryTime != null && System.currentTimeMillis() > expiryTime) {
            localCache.remove(cacheKey);
            expiryTimes.remove(cacheKey);
        }
        
        // 查询本地缓存
        EntityMetadata metadata = localCache.get(cacheKey);
        if (metadata != null) {
            stats.incrementHits();
            return metadata;
        }
        
        stats.incrementMisses();
        return null;
    }
    
    @Override
    public Map<String, EntityMetadata> batchGet(String tenantId, Collection<String> entityNames) {
        Map<String, EntityMetadata> result = new HashMap<>(entityNames.size());
        
        // 批量查询本地缓存
        for (String entityName : entityNames) {
            String cacheKey = buildCacheKey(tenantId, entityName);
            
            // 检查缓存是否过期
            Long expiryTime = expiryTimes.get(cacheKey);
            if (expiryTime != null && System.currentTimeMillis() > expiryTime) {
                localCache.remove(cacheKey);
                expiryTimes.remove(cacheKey);
                stats.incrementMisses();
                continue;
            }
            
            EntityMetadata metadata = localCache.get(cacheKey);
            if (metadata != null) {
                result.put(entityName, metadata);
                stats.incrementHits();
            } else {
                stats.incrementMisses();
            }
        }
        
        return result;
    }
    
    @Override
    public void put(String tenantId, String entityName, EntityMetadata metadata) {
        String cacheKey = buildCacheKey(tenantId, entityName);
        
        // 更新本地缓存
        localCache.put(cacheKey, metadata);
        expiryTimes.put(cacheKey, System.currentTimeMillis() + CACHE_TTL_HOURS * 60 * 60 * 1000);
        stats.incrementPuts();
    }
    
    @Override
    public void batchPut(String tenantId, Map<String, EntityMetadata> metadataMap) {
        // 批量更新本地缓存
        long expiryTime = System.currentTimeMillis() + CACHE_TTL_HOURS * 60 * 60 * 1000;
        
        for (Map.Entry<String, EntityMetadata> entry : metadataMap.entrySet()) {
            String cacheKey = buildCacheKey(tenantId, entry.getKey());
            localCache.put(cacheKey, entry.getValue());
            expiryTimes.put(cacheKey, expiryTime);
        }
        
        stats.incrementPuts(metadataMap.size());
    }
    
    @Override
    public void remove(String tenantId, String entityName) {
        String cacheKey = buildCacheKey(tenantId, entityName);
        
        // 从本地缓存移除
        localCache.remove(cacheKey);
        expiryTimes.remove(cacheKey);
        stats.incrementRemovals();
    }
    
    @Override
    public void clearTenantCache(String tenantId) {
        // 清除本地缓存中该租户的所有元数据
        String tenantPrefix = tenantId + ":";
        
        // 使用迭代器安全删除符合条件的键
        localCache.keySet().removeIf(key -> key.startsWith(tenantPrefix));
        expiryTimes.keySet().removeIf(key -> key.startsWith(tenantPrefix));
        
        stats.incrementFlushes();
    }
    
    @Override
    public void refresh(String tenantId, String entityName) {
        // 强制重新加载：先移除缓存，再获取会自动重新加载
        remove(tenantId, entityName);
        get(tenantId, entityName);
    }
    
    @Override
    public CacheStats getStats() {
        return stats;
    }
    
    // 构建本地缓存键
    private String buildCacheKey(String tenantId, String entityName) {
        return tenantId + ":" + entityName;
    }
    
    // 不需要Redis相关的方法
    
    /**
     * 缓存统计信息接口
     */
    public interface CacheStats {
        long getHits();
        long getMisses();
        long getRemoteHits();
        long getPuts();
        long getRemovals();
        long getFlushes();
        void incrementHits();
        void incrementMisses();
        void incrementRemoteHits();
        void incrementPuts();
        void incrementPuts(int count);
        void incrementRemovals();
        void incrementFlushes();
    }
    
    /**
     * 默认的缓存统计实现
     */
    private static class DefaultCacheStats implements CacheStats {
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        private final AtomicLong remoteHits = new AtomicLong(0);
        private final AtomicLong puts = new AtomicLong(0);
        private final AtomicLong removals = new AtomicLong(0);
        private final AtomicLong flushes = new AtomicLong(0);
        
        @Override
        public long getHits() {
            return hits.get();
        }
        
        @Override
        public long getMisses() {
            return misses.get();
        }
        
        @Override
        public long getRemoteHits() {
            return remoteHits.get();
        }
        
        @Override
        public long getPuts() {
            return puts.get();
        }
        
        @Override
        public long getRemovals() {
            return removals.get();
        }
        
        @Override
        public long getFlushes() {
            return flushes.get();
        }
        
        @Override
        public void incrementHits() {
            hits.incrementAndGet();
        }
        
        @Override
        public void incrementMisses() {
            misses.incrementAndGet();
        }
        
        @Override
        public void incrementRemoteHits() {
            remoteHits.incrementAndGet();
        }
        
        @Override
        public void incrementPuts() {
            puts.incrementAndGet();
        }
        
        @Override
        public void incrementPuts(int count) {
            puts.addAndGet(count);
        }
        
        @Override
        public void incrementRemovals() {
            removals.incrementAndGet();
        }
        
        @Override
        public void incrementFlushes() {
            flushes.incrementAndGet();
        }
    }
}