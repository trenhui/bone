package com.bone.metadata.catalog.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.sdk.Repository;
import java.util.Optional;
import org.springframework.util.StringUtils;

/**
 * 实体建模聚合仓储。
 *
 * <p>读模型方法（ADR-0030：写侧与领域读模型合并于仓储）下沉至此；应用层仅调用这些读方法， 不直接持有 FluentQuery / Criteria 等读侧 DSL（见 arch
 * rule readSideDslOnlyInQueryLayer）。
 */
public interface MetaEntityRepository extends Repository<MetaEntity, Long> {

  default PageResult<MetaEntity> pageEntities(
      long tenantId, String keyword, Integer status, Long moduleId, int pageNum, int pageSize) {
    // SDK 的 deleteById 对 meta_entity 走逻辑删除（deleted=1），读侧必须显式过滤，
    // 否则已删除实体仍会出现在列表中。
    var query =
        query().where(MetaEntity::getTenantId).eq(tenantId).and(MetaEntity::getDeleted).eq(false);
    if (moduleId != null) {
      query = query.and(MetaEntity::getModuleId).eq(moduleId);
    }
    if (StringUtils.hasText(keyword)) {
      query = query.and(MetaEntity::getName).like("%" + keyword.trim() + "%");
    }
    if (status != null) {
      query = query.and(MetaEntity::getStatus).eq(status);
    }
    return query.orderByDesc(MetaEntity::getId).page(pageNum, pageSize);
  }

  default Optional<MetaEntity> findByTenantAndCode(long tenantId, String code) {
    return where(MetaEntity::getTenantId).eq(tenantId).and(MetaEntity::getCode).eq(code).first();
  }

  /** 按租户 + 表名定位（创建实体时预检 uk_meta_e_table，避免重复键异常被兜成 500）。 */
  default Optional<MetaEntity> findByTenantAndTableName(long tenantId, String tableName) {
    return where(MetaEntity::getTenantId)
        .eq(tenantId)
        .and(MetaEntity::getTableName)
        .eq(tableName)
        .first();
  }
}
