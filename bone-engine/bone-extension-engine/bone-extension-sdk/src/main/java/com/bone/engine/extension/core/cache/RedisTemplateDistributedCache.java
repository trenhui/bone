package com.bone.engine.extension.core.cache;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * RedisTemplate分布式缓存实现
 *
 * <p>使用Spring Data Redis的RedisTemplate实现分布式缓存功能
 *
 * @since 1.0.0
 */
public class RedisTemplateDistributedCache implements DistributedCache {

  private static final Logger log = LoggerFactory.getLogger(RedisTemplateDistributedCache.class);

  private final RedisTemplate<String, Object> redisTemplate;
  private final String prefix;

  public RedisTemplateDistributedCache(RedisTemplate<String, Object> redisTemplate, String prefix) {
    this.redisTemplate = redisTemplate;
    this.prefix = prefix;
  }

  @Override
  public Object get(String key) {
    try {
      String cacheKey = buildCacheKey(key);
      return redisTemplate.opsForValue().get(cacheKey);
    } catch (Exception e) {
      log.error("Failed to get from distributed cache: {}", key, e);
    }
    return null;
  }

  @Override
  public void set(String key, Object value, long expireSeconds) {
    try {
      String cacheKey = buildCacheKey(key);
      redisTemplate.opsForValue().set(cacheKey, value, expireSeconds, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error("Failed to set to distributed cache: {}", key, e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      String cacheKey = buildCacheKey(key);
      redisTemplate.delete(cacheKey);
    } catch (Exception e) {
      log.error("Failed to delete from distributed cache: {}", key, e);
    }
  }

  @Override
  public void clear() {
    try {
      redisTemplate.delete(redisTemplate.keys(prefix + "*"));
    } catch (Exception e) {
      log.error("Failed to clear distributed cache", e);
    }
  }

  @Override
  public boolean exists(String key) {
    try {
      String cacheKey = buildCacheKey(key);
      return redisTemplate.hasKey(cacheKey);
    } catch (Exception e) {
      log.error("Failed to check existence in distributed cache: {}", key, e);
      return false;
    }
  }

  private String buildCacheKey(String key) {
    return prefix + ":" + key;
  }
}
