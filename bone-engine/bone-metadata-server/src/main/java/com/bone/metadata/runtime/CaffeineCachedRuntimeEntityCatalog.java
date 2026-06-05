package com.bone.metadata.runtime;

import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Optional;

/** 进程内 Caffeine 缓存（单节点默认）。 */
public class CaffeineCachedRuntimeEntityCatalog
    implements RuntimeEntityCatalog, RuntimeEntityCacheEvictor {

  private final CatalogRuntimeEntityProvider delegate;
  private final Cache<String, Optional<PublishedRuntimeEntity>> cache;

  public CaffeineCachedRuntimeEntityCatalog(
      CatalogRuntimeEntityProvider delegate, Duration cacheTtl) {
    this.delegate = delegate;
    this.cache = Caffeine.newBuilder().expireAfterWrite(cacheTtl).maximumSize(10_000).build();
  }

  @Override
  public Optional<PublishedRuntimeEntity> findPublishedRuntime(String entityCode, long tenantId) {
    String key = cacheKey(tenantId, entityCode);
    return cache.get(key, k -> delegate.findPublishedRuntime(entityCode, tenantId));
  }

  @Override
  public void evict(String entityCode, long tenantId) {
    cache.invalidate(cacheKey(tenantId, entityCode));
  }

  private static String cacheKey(long tenantId, String entityCode) {
    return tenantId + "|" + entityCode;
  }
}
