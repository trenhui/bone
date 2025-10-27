package com.bone.engine.extension.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis扩展点仓库实现，提供分布式环境下的扩展点存储和管理
 * <p>
 * 使用Redis作为存储后端，支持扩展点的跨实例共享和管理
 * 适用于微服务架构和分布式部署场景
 *
 * @author renhui.trh
 * @since 1.0.0
 */
public class RedisExtPointRepository implements ExtPointRepository {
    private static final Logger log = LoggerFactory.getLogger(RedisExtPointRepository.class);
    
    // Redis键前缀，用于区分不同类型的数据
    private static final String EXTENSION_PREFIX = "bone:extension:";
    
    // 默认过期时间（毫秒），可通过配置修改
    private static final long DEFAULT_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24小时
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisSerializer keySerializer;
    private final JdkSerializationRedisSerializer valueSerializer;
    private final long expiryMs;
    
    /**
     * 构造函数，通过Spring注入RedisTemplate
     * 
     * @param redisTemplate Redis模板，非空
     */
    @Autowired
    public RedisExtPointRepository(RedisTemplate<String, Object> redisTemplate) {
        Assert.notNull(redisTemplate, "RedisTemplate must not be null");
        this.redisTemplate = redisTemplate;
        this.keySerializer = new StringRedisSerializer();
        this.valueSerializer = new JdkSerializationRedisSerializer();
        this.expiryMs = DEFAULT_EXPIRY_MS;
    }
    
    /**
     * 使用指定过期时间的构造函数
     * 
     * @param redisTemplate Redis模板，非空
     * @param expiryMs 过期时间（毫秒）
     */
    public RedisExtPointRepository(RedisTemplate<String, Object> redisTemplate, long expiryMs) {
        Assert.notNull(redisTemplate, "RedisTemplate must not be null");
        Assert.isTrue(expiryMs > 0, "Expiry time must be greater than 0");
        this.redisTemplate = redisTemplate;
        this.keySerializer = new StringRedisSerializer();
        this.valueSerializer = new JdkSerializationRedisSerializer();
        this.expiryMs = expiryMs;
    }
    
    /**
     * 获取Redis键
     * 
     * @param key 原始键
     * @return Redis键
     */
    private String getRedisKey(Object key) {
        return EXTENSION_PREFIX + key;
    }
    
    /**
     * 根据键获取扩展实现
     * 
     * @param key 扩展实现的键
     * @return 对应的扩展实现，如果不存在则返回null
     * @throws IllegalArgumentException 当key为null时抛出
     */
    @Override
    @Nullable
    public Object get(Object key) {
        Assert.notNull(key, "Extension key must not be null");
        
        String redisKey = getRedisKey(key);
        log.debug("Getting extension from Redis with key: {}", redisKey);
        
        try {
            Object value = redisTemplate.opsForValue().get(redisKey);
            log.debug("Retrieved extension from Redis: key={}, value={}", redisKey, value != null ? value.getClass().getName() : null);
            return value;
        } catch (Exception e) {
            log.error("Failed to get extension from Redis: {}", redisKey, e);
            return null;
        }
    }
    
    /**
     * 存储扩展实现
     * 
     * @param key 扩展实现的键
     * @param value 扩展实现实例
     * @return 先前关联到此键的值，如果不存在则返回null
     * @throws IllegalArgumentException 当key或value为null时抛出
     */
    @Override
    @Nullable
    public Object put(Object key, Object value) {
        Assert.notNull(key, "Extension key must not be null");
        Assert.notNull(value, "Extension value must not be null");
        
        String redisKey = getRedisKey(key);
        log.debug("Putting extension into Redis with key: {}, value type: {}", redisKey, value.getClass().getName());
        
        try {
            // 获取旧值
            Object oldValue = redisTemplate.opsForValue().getAndSet(redisKey, value);
            
            // 设置过期时间
            redisTemplate.expire(redisKey, expiryMs, TimeUnit.MILLISECONDS);
            
            log.debug("Stored extension in Redis: key={}, oldValue={}, newValue={}", 
                    redisKey, oldValue != null ? oldValue.getClass().getName() : null, value.getClass().getName());
            
            return oldValue;
        } catch (Exception e) {
            log.error("Failed to put extension into Redis: {}", redisKey, e);
            throw new RuntimeException("Failed to store extension in Redis", e);
        }
    }
    
    /**
     * 移除指定键的扩展实现
     * 
     * @param key 要移除的扩展实现的键
     * @return 被移除的值，如果不存在则返回null
     * @throws IllegalArgumentException 当key为null时抛出
     */
    @Override
    @Nullable
    public Object remove(Object key) {
        Assert.notNull(key, "Extension key must not be null");
        
        String redisKey = getRedisKey(key);
        log.debug("Removing extension from Redis with key: {}", redisKey);
        
        try {
            // 获取旧值
            Object oldValue = redisTemplate.opsForValue().get(redisKey);
            
            // 删除键
            boolean deleted = redisTemplate.delete(redisKey);
            
            log.debug("Removed extension from Redis: key={}, success={}, value={}", 
                    redisKey, deleted, oldValue != null ? oldValue.getClass().getName() : null);
            
            return oldValue;
        } catch (Exception e) {
            log.error("Failed to remove extension from Redis: {}", redisKey, e);
            return null;
        }
    }
    
    /**
     * 清除所有扩展实现
     * 通常在系统重启或配置刷新时调用
     */
    @Override
    public void clear() {
        log.info("Clearing all extensions from Redis");
        
        try {
            // 获取所有匹配的键
            Set<String> keys = redisTemplate.keys(EXTENSION_PREFIX + "*");
            
            if (keys != null && !keys.isEmpty()) {
                long deletedCount = redisTemplate.delete(keys);
                log.info("Cleared {} extensions from Redis", deletedCount);
            } else {
                log.info("No extensions found to clear from Redis");
            }
        } catch (Exception e) {
            log.error("Failed to clear extensions from Redis", e);
        }
    }
    
    /**
     * 获取当前存储的扩展点数量
     * 
     * @return 扩展点数量
     */
    public int size() {
        try {
            Set<String> keys = redisTemplate.keys(EXTENSION_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("Failed to get extension count from Redis", e);
            return 0;
        }
    }
    
    /**
     * 检查是否包含指定key的扩展点
     * 
     * @param key 扩展点标识
     * @return 如果存在则返回true，否则返回false
     * @throws IllegalArgumentException 当key为null时抛出
     */
    public boolean containsKey(Object key) {
        Assert.notNull(key, "Extension key must not be null");
        
        String redisKey = getRedisKey(key);
        
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        } catch (Exception e) {
            log.error("Failed to check if key exists in Redis: {}", redisKey, e);
            return false;
        }
    }
    
    /**
     * 获取所有存储的扩展点键集合
     * 
     * @return 扩展点键集合
     */
    public Set<Object> keySet() {
        try {
            Set<String> redisKeys = redisTemplate.keys(EXTENSION_PREFIX + "*");
            if (redisKeys != null && !redisKeys.isEmpty()) {
                // 移除前缀，返回原始键
                return redisKeys.stream()
                        .map(key -> key.substring(EXTENSION_PREFIX.length()))
                        .collect(Collectors.toSet());
            }
            return java.util.Collections.emptySet();
        } catch (Exception e) {
            log.error("Failed to get extension keys from Redis", e);
            return java.util.Collections.emptySet();
        }
    }
}
