package com.bone.metadata.engine.runtime.cache;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import java.util.Collection;
import java.util.Map;

/** 元数据缓存管理器接口 支持多级缓存策略：本地缓存 + 分布式缓存 */
public interface MetadataCacheManager {

  /** 获取元数据（从多级缓存） */
  EntityMetadata get(String tenantId, String entityName);

  /** 批量获取元数据 */
  Map<String, EntityMetadata> batchGet(String tenantId, Collection<String> entityNames);

  /** 存储元数据到缓存 */
  void put(String tenantId, String entityName, EntityMetadata metadata);

  /** 批量存储元数据 */
  void batchPut(String tenantId, Map<String, EntityMetadata> metadataMap);

  /** 从缓存中移除元数据 */
  void remove(String tenantId, String entityName);

  /** 清除特定租户的缓存 */
  void clearTenantCache(String tenantId);

  /** 刷新缓存 */
  void refresh(String tenantId, String entityName);

  /** 获取缓存统计信息 */
  CacheStats getStats();
}
