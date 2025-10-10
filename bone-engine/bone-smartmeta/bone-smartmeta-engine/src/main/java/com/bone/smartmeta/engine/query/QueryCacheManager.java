package com.bone.smartmeta.engine.query;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查询缓存管理器，用于缓存和管理查询结果
 */
@Component
public class QueryCacheManager {
    
    // 简单的内存缓存实现
    private final Map<String, CachedQueryResult> cache = new ConcurrentHashMap<>();
    
    /**
     * 生成缓存键
     * @param smartql SmartQL查询字符串
     * @param parameters 查询参数
     * @param userId 用户ID
     * @return 缓存键
     */
    public String generateCacheKey(String smartql, Map<String, Object> parameters, String userId) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(userId).append(":");
        keyBuilder.append(smartql);
        
        // 添加参数到缓存键
        if (parameters != null && !parameters.isEmpty()) {
            keyBuilder.append(":params:").append(parameters.toString());
        }
        
        return keyBuilder.toString();
    }
    
    /**
     * 从缓存中获取查询结果
     * @param cacheKey 缓存键
     * @return 查询结果，如果缓存未命中则返回null
     */
    public Object getFromCache(String cacheKey) {
        CachedQueryResult cachedResult = cache.get(cacheKey);
        
        if (cachedResult != null) {
            // 检查是否过期
            if (cachedResult.isExpired()) {
                cache.remove(cacheKey);
                return null;
            }
            return cachedResult.getResult();
        }
        
        return null;
    }
    
    /**
     * 缓存查询结果
     * @param cacheKey 缓存键
     * @param result 查询结果
     * @param ttl 过期时间（毫秒）
     */
    public void cacheResult(String cacheKey, Object result, long ttl) {
        // 默认TTL为5分钟
        if (ttl <= 0) {
            ttl = 5 * 60 * 1000;
        }
        
        long expirationTime = System.currentTimeMillis() + ttl;
        cache.put(cacheKey, new CachedQueryResult(result, expirationTime));
        
        // 简单的缓存清理（实际项目中可能需要更复杂的缓存管理策略）
        cleanupExpiredCache();
    }
    
    /**
     * 清除指定缓存
     * @param cacheKey 缓存键
     */
    public void invalidateCache(String cacheKey) {
        cache.remove(cacheKey);
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCache() {
        cache.clear();
    }
    
    /**
     * 清理过期缓存
     */
    private void cleanupExpiredCache() {
        // 当缓存大小超过一定阈值时进行清理
        if (cache.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));
        }
    }
    
    /**
     * 缓存查询结果的内部类
     */
    private static class CachedQueryResult {
        private final Object result;
        private final long expirationTime;
        
        public CachedQueryResult(Object result, long expirationTime) {
            this.result = result;
            this.expirationTime = expirationTime;
        }
        
        public Object getResult() {
            return result;
        }
        
        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }
        
        public boolean isExpired(long currentTime) {
            return currentTime > expirationTime;
        }
    }
}