package com.bone.metadata.catalog.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.domain.model.MetaEntity;
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
      long tenantId, String keyword, Integer status, int pageNum, int pageSize) {
    var query = query().where(MetaEntity::getTenantId).eq(tenantId);
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
}
