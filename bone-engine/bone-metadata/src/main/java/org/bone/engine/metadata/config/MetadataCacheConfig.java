package org.bone.engine.metadata.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 元数据缓存配置类 - 提供元数据缓存功能的配置
 * 
 * @author Bone Engine Team
 */
@Configuration
public class MetadataCacheConfig {
    
    /**
     * 注册内存元数据缓存实现
     */
    @Bean
    @ConditionalOnMissingBean(MetadataCache.class)
    @ConditionalOnProperty(name = "bone.metadata.cache.enabled", havingValue = "true", matchIfMissing = true)
    public MetadataCache metadataCache(MetadataProperties properties) {
        return new InMemoryMetadataCache(properties.getCacheTTL(), properties.getCacheMaxSize());
    }
    
    /**
     * 注册元数据缓存管理器
     */
    @Bean
    @ConditionalOnMissingBean(MetadataCacheManager.class)
    @ConditionalOnProperty(name = "bone.metadata.cache.enabled", havingValue = "true", matchIfMissing = true)
    public MetadataCacheManager metadataCacheManager(MetadataCache metadataCache) {
        return new DefaultMetadataCacheManager(metadataCache);
    }
    
    /**
     * 注册元数据缓存键生成器
     */
    @Bean
    @ConditionalOnMissingBean(MetadataCacheKeyGenerator.class)
    @ConditionalOnProperty(name = "bone.metadata.cache.enabled", havingValue = "true", matchIfMissing = true)
    public MetadataCacheKeyGenerator metadataCacheKeyGenerator() {
        return new DefaultMetadataCacheKeyGenerator();
    }
    
    // ========== 缓存接口定义 ==========
    
    /**
     * 元数据缓存接口
     */
    public interface MetadataCache {
        /**
         * 获取缓存项
         */
        <T> T get(String key, Class<T> type);
        
        /**
         * 设置缓存项
         */
        void put(String key, Object value);
        
        /**
         * 设置带过期时间的缓存项
         */
        void put(String key, Object value, long ttl, TimeUnit timeUnit);
        
        /**
         * 删除缓存项
         */
        void remove(String key);
        
        /**
         * 删除匹配模式的缓存项
         */
        void removePattern(String pattern);
        
        /**
         * 清空所有缓存
         */
        void clear();
        
        /**
         * 检查是否存在缓存项
         */
        boolean contains(String key);
        
        /**
         * 获取缓存大小
         */
        int size();
        
        /**
         * 获取缓存统计信息
         */
        CacheStats getStats();
        
        /**
         * 刷新缓存
         */
        void refresh();
    }
    
    /**
     * 元数据缓存管理器接口
     */
    public interface MetadataCacheManager {
        /**
         * 获取缓存
         */
        <T> T getCache(String cacheName, String key, Class<T> type);
        
        /**
         * 放入缓存
         */
        void putCache(String cacheName, String key, Object value);
        
        /**
         * 放入带过期时间的缓存
         */
        void putCache(String cacheName, String key, Object value, long ttl, TimeUnit timeUnit);
        
        /**
         * 删除缓存
         */
        void removeCache(String cacheName, String key);
        
        /**
         * 删除缓存区域
         */
        void removeCacheArea(String cacheName);
        
        /**
         * 清空所有缓存
         */
        void clearAll();
        
        /**
         * 获取缓存区域
         */
        Map<String, Object> getCacheArea(String cacheName);
        
        /**
         * 检查缓存是否存在
         */
        boolean exists(String cacheName, String key);
        
        /**
         * 获取缓存统计信息
         */
        CacheStats getStats(String cacheName);
    }
    
    /**
     * 元数据缓存键生成器接口
     */
    public interface MetadataCacheKeyGenerator {
        /**
         * 生成缓存键
         */
        String generateKey(String prefix, String... parts);
        
        /**
         * 生成实体元数据缓存键
         */
        String generateEntityKey(String apiName);
        
        /**
         * 生成字段元数据缓存键
         */
        String generateFieldKey(String entityApiName, String fieldName);
        
        /**
         * 生成关系元数据缓存键
         */
        String generateRelationshipKey(String entityApiName, String relationshipName);
        
        /**
         * 生成查询缓存键
         */
        String generateQueryKey(String query, Map<String, Object> params);
    }
    
    // ========== 缓存统计类 ==========
    
    /**
     * 缓存统计信息类
     */
    public static class CacheStats {
        private long hits = 0;
        private long misses = 0;
        private long puts = 0;
        private long removes = 0;
        private long evictions = 0;
        private long maxSize;
        private long currentSize;
        private long avgGetTimeMs = 0;
        private long avgPutTimeMs = 0;
        
        // Getters and Setters
        public long getHits() {
            return hits;
        }
        public void setHits(long hits) {
            this.hits = hits;
        }
        public void incrementHits() {
            this.hits++;
        }
        public long getMisses() {
            return misses;
        }
        public void setMisses(long misses) {
            this.misses = misses;
        }
        public void incrementMisses() {
            this.misses++;
        }
        public long getPuts() {
            return puts;
        }
        public void setPuts(long puts) {
            this.puts = puts;
        }
        public void incrementPuts() {
            this.puts++;
        }
        public long getRemoves() {
            return removes;
        }
        public void setRemoves(long removes) {
            this.removes = removes;
        }
        public void incrementRemoves() {
            this.removes++;
        }
        public long getEvictions() {
            return evictions;
        }
        public void setEvictions(long evictions) {
            this.evictions = evictions;
        }
        public void incrementEvictions() {
            this.evictions++;
        }
        public long getMaxSize() {
            return maxSize;
        }
        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }
        public long getCurrentSize() {
            return currentSize;
        }
        public void setCurrentSize(long currentSize) {
            this.currentSize = currentSize;
        }
        public long getAvgGetTimeMs() {
            return avgGetTimeMs;
        }
        public void setAvgGetTimeMs(long avgGetTimeMs) {
            this.avgGetTimeMs = avgGetTimeMs;
        }
        public long getAvgPutTimeMs() {
            return avgPutTimeMs;
        }
        public void setAvgPutTimeMs(long avgPutTimeMs) {
            this.avgPutTimeMs = avgPutTimeMs;
        }
        
        /**
         * 计算命中率
         */
        public double getHitRate() {
            long total = hits + misses;
            return total > 0 ? (double) hits / total : 0;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, puts=%d, removes=%d, evictions=%d, size=%d/%d}",
                    hits, misses, getHitRate() * 100, puts, removes, evictions, currentSize, maxSize);
        }
    }
    
    // ========== 缓存条目类 ==========
    
    /**
     * 缓存条目类
     */
    public static class CacheEntry {
        private final Object value;
        private final long creationTime;
        private final long expirationTime;
        
        public CacheEntry(Object value, long ttl, TimeUnit timeUnit) {
            this.value = value;
            this.creationTime = System.currentTimeMillis();
            this.expirationTime = ttl > 0 ? this.creationTime + timeUnit.toMillis(ttl) : Long.MAX_VALUE;
        }
        
        public Object getValue() {
            return value;
        }
        
        public long getCreationTime() {
            return creationTime;
        }
        
        public long getExpirationTime() {
            return expirationTime;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
        
        public long getTimeToLiveMs() {
            return Math.max(0, expirationTime - System.currentTimeMillis());
        }
    }
    
    // ========== 内存缓存实现 ==========
    
    /**
     * 内存元数据缓存实现
     */
    public static class InMemoryMetadataCache implements MetadataCache {
        private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
        private final long defaultTTL; // 默认过期时间（毫秒）
        private final int maxSize; // 最大缓存条目数
        private final CacheStats stats = new CacheStats();
        
        public InMemoryMetadataCache(long ttl, int maxSize) {
            this.defaultTTL = ttl;
            this.maxSize = maxSize;
            this.stats.setMaxSize(maxSize);
            
            // 启动后台清理线程
            startCleanupThread();
        }
        
        @Override
        public <T> T get(String key, Class<T> type) {
            long startTime = System.currentTimeMillis();
            
            try {
                CacheEntry entry = cache.get(key);
                if (entry != null) {
                    if (entry.isExpired()) {
                        // 过期条目自动删除
                        cache.remove(key);
                        stats.incrementMisses();
                        stats.setCurrentSize(cache.size());
                        return null;
                    }
                    
                    stats.incrementHits();
                    return type.cast(entry.getValue());
                }
                
                stats.incrementMisses();
                return null;
            } finally {
                // 更新平均获取时间
                long endTime = System.currentTimeMillis();
                long getTime = endTime - startTime;
                stats.setAvgGetTimeMs((stats.getAvgGetTimeMs() * stats.getHits() + getTime) / (stats.getHits() + 1));
            }
        }
        
        @Override
        public void put(String key, Object value) {
            put(key, value, defaultTTL, TimeUnit.MILLISECONDS);
        }
        
        @Override
        public void put(String key, Object value, long ttl, TimeUnit timeUnit) {
            long startTime = System.currentTimeMillis();
            
            try {
                // 检查缓存大小限制
                if (cache.size() >= maxSize && !cache.containsKey(key)) {
                    evictOldest();
                    stats.incrementEvictions();
                }
                
                cache.put(key, new CacheEntry(value, ttl, timeUnit));
                stats.incrementPuts();
                stats.setCurrentSize(cache.size());
            } finally {
                // 更新平均放置时间
                long endTime = System.currentTimeMillis();
                long putTime = endTime - startTime;
                stats.setAvgPutTimeMs((stats.getAvgPutTimeMs() * stats.getPuts() + putTime) / (stats.getPuts() + 1));
            }
        }
        
        @Override
        public void remove(String key) {
            cache.remove(key);
            stats.incrementRemoves();
            stats.setCurrentSize(cache.size());
        }
        
        @Override
        public void removePattern(String pattern) {
            int removed = 0;
            for (String key : cache.keySet()) {
                if (key.matches(pattern)) {
                    cache.remove(key);
                    removed++;
                }
            }
            stats.setRemoves(stats.getRemoves() + removed);
            stats.setCurrentSize(cache.size());
        }
        
        @Override
        public void clear() {
            cache.clear();
            stats.setRemoves(stats.getRemoves() + stats.getCurrentSize());
            stats.setCurrentSize(0);
        }
        
        @Override
        public boolean contains(String key) {
            CacheEntry entry = cache.get(key);
            if (entry != null && entry.isExpired()) {
                // 过期条目自动删除
                remove(key);
                return false;
            }
            return entry != null;
        }
        
        @Override
        public int size() {
            // 清理过期条目
            cleanupExpired();
            return cache.size();
        }
        
        @Override
        public CacheStats getStats() {
            stats.setCurrentSize(cache.size());
            return stats;
        }
        
        @Override
        public void refresh() {
            cleanupExpired();
        }
        
        /**
         * 驱逐最老的条目
         */
        private void evictOldest() {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                if (entry.getValue().getCreationTime() < oldestTime) {
                    oldestTime = entry.getValue().getCreationTime();
                    oldestKey = entry.getKey();
                }
            }
            
            if (oldestKey != null) {
                cache.remove(oldestKey);
            }
        }
        
        /**
         * 清理过期条目
         */
        private void cleanupExpired() {
            int removed = 0;
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                if (entry.getValue().isExpired()) {
                    cache.remove(entry.getKey());
                    removed++;
                }
            }
            
            if (removed > 0) {
                stats.setRemoves(stats.getRemoves() + removed);
                stats.setCurrentSize(cache.size());
            }
        }
        
        /**
         * 启动后台清理线程
         */
        private void startCleanupThread() {
            Thread cleanupThread = new Thread(() -> {
                while (true) {
                    try {
                        // 每5分钟清理一次
                        Thread.sleep(5 * 60 * 1000);
                        cleanupExpired();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "metadata-cache-cleanup");
            
            cleanupThread.setDaemon(true);
            cleanupThread.start();
        }
    }
    
    // ========== 缓存管理器实现 ==========
    
    /**
     * 默认元数据缓存管理器实现
     */
    public static class DefaultMetadataCacheManager implements MetadataCacheManager {
        private final MetadataCache metadataCache;
        private final Map<String, CacheStats> areaStats = new ConcurrentHashMap<>();
        
        public DefaultMetadataCacheManager(MetadataCache metadataCache) {
            this.metadataCache = metadataCache;
        }
        
        @Override
        public <T> T getCache(String cacheName, String key, Class<T> type) {
            String fullKey = cacheName + ":" + key;
            T result = metadataCache.get(fullKey, type);
            
            // 更新区域统计
            CacheStats stats = getOrCreateAreaStats(cacheName);
            if (result != null) {
                stats.incrementHits();
            } else {
                stats.incrementMisses();
            }
            
            return result;
        }
        
        @Override
        public void putCache(String cacheName, String key, Object value) {
            String fullKey = cacheName + ":" + key;
            metadataCache.put(fullKey, value);
            
            // 更新区域统计
            CacheStats stats = getOrCreateAreaStats(cacheName);
            stats.incrementPuts();
        }
        
        @Override
        public void putCache(String cacheName, String key, Object value, long ttl, TimeUnit timeUnit) {
            String fullKey = cacheName + ":" + key;
            metadataCache.put(fullKey, value, ttl, timeUnit);
            
            // 更新区域统计
            CacheStats stats = getOrCreateAreaStats(cacheName);
            stats.incrementPuts();
        }
        
        @Override
        public void removeCache(String cacheName, String key) {
            String fullKey = cacheName + ":" + key;
            metadataCache.remove(fullKey);
            
            // 更新区域统计
            CacheStats stats = getOrCreateAreaStats(cacheName);
            stats.incrementRemoves();
        }
        
        @Override
        public void removeCacheArea(String cacheName) {
            metadataCache.removePattern("^" + cacheName + ":.*$");
            
            // 重置区域统计
            areaStats.remove(cacheName);
        }
        
        @Override
        public void clearAll() {
            metadataCache.clear();
            areaStats.clear();
        }
        
        @Override
        public Map<String, Object> getCacheArea(String cacheName) {
            // 简化实现，实际应该返回区域内所有缓存项
            return new ConcurrentHashMap<>();
        }
        
        @Override
        public boolean exists(String cacheName, String key) {
            String fullKey = cacheName + ":" + key;
            return metadataCache.contains(fullKey);
        }
        
        @Override
        public CacheStats getStats(String cacheName) {
            return getOrCreateAreaStats(cacheName);
        }
        
        /**
         * 获取或创建区域统计信息
         */
        private CacheStats getOrCreateAreaStats(String cacheName) {
            return areaStats.computeIfAbsent(cacheName, k -> new CacheStats());
        }
    }
    
    // ========== 缓存键生成器实现 ==========
    
    /**
     * 默认元数据缓存键生成器实现
     */
    public static class DefaultMetadataCacheKeyGenerator implements MetadataCacheKeyGenerator {
        
        @Override
        public String generateKey(String prefix, String... parts) {
            StringBuilder sb = new StringBuilder(prefix);
            for (String part : parts) {
                sb.append(":").append(part);
            }
            return sb.toString();
        }
        
        @Override
        public String generateEntityKey(String apiName) {
            return generateKey("entity", apiName);
        }
        
        @Override
        public String generateFieldKey(String entityApiName, String fieldName) {
            return generateKey("field", entityApiName, fieldName);
        }
        
        @Override
        public String generateRelationshipKey(String entityApiName, String relationshipName) {
            return generateKey("relationship", entityApiName, relationshipName);
        }
        
        @Override
        public String generateQueryKey(String query, Map<String, Object> params) {
            StringBuilder sb = new StringBuilder("query:" + query);
            if (params != null && !params.isEmpty()) {
                // 对参数键进行排序以确保一致性
                params.keySet().stream().sorted().forEach(key -> {
                    sb.append(":").append(key).append("=")
                      .append(params.get(key) != null ? params.get(key).toString() : "null");
                });
            }
            return sb.toString();
        }
    }
}