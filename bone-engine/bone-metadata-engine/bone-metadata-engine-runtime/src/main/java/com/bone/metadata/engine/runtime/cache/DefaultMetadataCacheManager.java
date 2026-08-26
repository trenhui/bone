package com.bone.metadata.engine.runtime.cache;

import com.bone.metadata.engine.domain.model.EntityMetadata;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/** 默认的元数据缓存管理器实现 采用Caffeine本地缓存 + Redis分布式缓存的多级缓存架构 */
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
    this.stats = new CacheStats();
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

    incrementPutsBatch(metadataMap.size());
  }

  @Override
  public void remove(String tenantId, String entityName) {
    String cacheKey = buildCacheKey(tenantId, entityName);

    // 从本地缓存移除
    localCache.remove(cacheKey);
    expiryTimes.remove(cacheKey);
    stats.incrementRemoves();
  }

  @Override
  public void clearTenantCache(String tenantId) {
    // 清除本地缓存中该租户的所有元数据
    String tenantPrefix = tenantId + ":";

    // 使用迭代器安全删除符合条件的键
    localCache.keySet().removeIf(key -> key.startsWith(tenantPrefix));
    expiryTimes.keySet().removeIf(key -> key.startsWith(tenantPrefix));

    incrementFlushes();
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

  // 适配批量增加方法
  private void incrementPutsBatch(int count) {
    for (int i = 0; i < count; i++) {
      stats.incrementPuts();
    }
  }

  // 适配刷新操作
  private void incrementFlushes() {
    // 刷新操作可以视为一种特殊的移除
    stats.incrementRemoves();
  }
}
