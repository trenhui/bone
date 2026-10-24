package com.bone.airag.domain.repository;

import com.bone.airag.domain.model.RagChunk;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;

/** 切片仓储（由使用方通过 {@code @EnableSqlRepositories} 自动实现，SDK 代理）。 */
public interface ChunkRepository extends Repository<RagChunk, Long> {

  /** 租户隔离 + 可选文档作用域过滤（权限在网关层，此处仅租户硬隔离）。 */
  default List<RagChunk> findByTenantAndDocScope(Long tenantId, String docScope) {
    List<RagChunk> all =
        QueryBuilder.from(RagChunk.class).where(RagChunk::getTenantId).eq(tenantId).list();
    if (docScope == null || docScope.isBlank()) {
      return all;
    }
    return all.stream().filter(c -> docScope.equals(c.getDocId())).collect(Collectors.toList());
  }
}
