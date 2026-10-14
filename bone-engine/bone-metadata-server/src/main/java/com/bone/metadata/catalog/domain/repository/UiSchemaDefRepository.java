package com.bone.metadata.catalog.domain.repository;

import com.bone.metadata.catalog.domain.model.ui.UiSchemaDef;
import com.bone.metadata.sdk.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 界面 schema 聚合仓储（团队级，键 (tenant_id, entity_id) 唯一）。
 *
 * <p>读模型方法下沉至此；应用层仅调用这些读方法，不直接持有读侧 DSL。
 */
public interface UiSchemaDefRepository extends Repository<UiSchemaDef, Long> {

  default Optional<UiSchemaDef> findByTenantAndEntity(long tenantId, long entityId) {
    return where(UiSchemaDef::getTenantId)
        .eq(tenantId)
        .and(UiSchemaDef::getEntityId)
        .eq(entityId)
        .and(UiSchemaDef::getDeleted)
        .eq(false)
        .first();
  }

  /** 列表页 hasUiSchema 批量填充（一次 IN 查询，禁 N+1）。 */
  default List<UiSchemaDef> findByTenantAndEntities(long tenantId, List<Long> entityIds) {
    if (entityIds == null || entityIds.isEmpty()) {
      return List.of();
    }
    return query()
        .where(UiSchemaDef::getTenantId)
        .eq(tenantId)
        .and(UiSchemaDef::getEntityId)
        .in(entityIds)
        .and(UiSchemaDef::getDeleted)
        .eq(false)
        .list();
  }
}
