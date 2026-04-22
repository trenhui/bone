package com.bone.engine.extension.core.cache;

import com.bone.engine.extension.support.config.ExtensionProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

/**
 * 缓存管理器
 * <p>
 * 管理本地缓存和分布式缓存的协同工作
 * </p>
 *
 * @since 1.0.0
 */
public class CacheManager {

    private final Cache<String, Object> localCache;
    private final DistributedCache distributedCache;
    private final boolean distributedEnabled;
    private final long distributedExpireSeconds;

    public CacheManager(ExtensionProperties properties, RedisTemplate<String, Object> redisTemplate) {
        ExtensionProperties.CacheConfig cacheConfig = properties.getCache();
        this.distributedEnabled = cacheConfig.isDistributedEnabled();
        this.distributedExpireSeconds = cacheConfig.getDistributedExpireSeconds();

        // 初始化本地缓存
        this.localCache = Caffeine.newBuilder()
                .maximumSize(cacheConfig.getMaxSize())
                .expireAfterWrite(Duration.ofMillis(cacheConfig.getExpireAfterWrite()))
                .recordStats()
                .build();

        // 初始化分布式缓存
        if (distributedEnabled && redisTemplate != null) {
            this.distributedCache = new RedisTemplateDistributedCache(
                    redisTemplate,
                    cacheConfig.getDistributedPrefix()
            );
        } else {
            this.distributedCache = null;
        }
    }

    /**
     * 获取缓存值
     * <p>
     * 优先从本地缓存获取，本地缓存未命中时从分布式缓存获取
     * </p>
     *
     * @param key 缓存键
     * @return 缓存值
     */
    public Object get(String key) {
        // 优先从本地缓存获取
        Object value = localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }

        // 本地缓存未命中，从分布式缓存获取
        if (distributedEnabled && distributedCache != null) {
            value = distributedCache.get(key);
            if (value != null) {
                // 将分布式缓存中的值同步到本地缓存
                localCache.put(key, value);
            }
        }

        return value;
    }

    /**
     * 设置缓存值
     * <p>
     * 同时设置本地缓存和分布式缓存
     * </p>
     *
     * @param key 缓存键
     * @param value 缓存值
     */
    public void put(String key, Object value) {
        // 设置本地缓存
        localCache.put(key, value);

        // 设置分布式缓存
        if (distributedEnabled && distributedCache != null) {
            distributedCache.set(key, value, distributedExpireSeconds);
        }
    }

    /**
     * 删除缓存
     * <p>
     * 同时删除本地缓存和分布式缓存
     * </p>
     *
     * @param key 缓存键
     */
    public void remove(String key) {
        // 删除本地缓存
        localCache.invalidate(key);

        // 删除分布式缓存
        if (distributedEnabled && distributedCache != null) {
            distributedCache.delete(key);
        }
    }

    /**
     * 清空缓存
     * <p>
     * 同时清空本地缓存和分布式缓存
     * </p>
     */
    public void clear() {
        // 清空本地缓存
        localCache.invalidateAll();

        // 清空分布式缓存
        if (distributedEnabled && distributedCache != null) {
            distributedCache.clear();
        }
    }

    /**
     * 获取本地缓存
     *
     * @return 本地缓存
     */
    public Cache<String, Object> getLocalCache() {
        return localCache;
    }

    /**
     * 获取分布式缓存
     *
     * @return 分布式缓存
     */
    public DistributedCache getDistributedCache() {
        return distributedCache;
    }

    /**
     * 是否启用分布式缓存
     *
     * @return 是否启用
     */
    public boolean isDistributedEnabled() {
        return distributedEnabled;
    }
}
