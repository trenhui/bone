package com.bone.metadata.runtime;

/** 发布/变更后失效已发布 RUNTIME 实体缓存。 */
public interface RuntimeEntityCacheEvictor {

  void evict(String entityCode, long tenantId);
}
