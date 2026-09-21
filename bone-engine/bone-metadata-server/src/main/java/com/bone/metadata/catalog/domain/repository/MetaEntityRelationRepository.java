package com.bone.metadata.catalog.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.domain.model.MetaEntityRelation;
import com.bone.metadata.sdk.Repository;
import org.springframework.util.StringUtils;

/** 关系聚合仓储。读模型方法（ADR-0030）下沉至此；应用层调用而非持有读侧 DSL。 */
public interface MetaEntityRelationRepository extends Repository<MetaEntityRelation, Long> {

  default PageResult<MetaEntityRelation> pageRelations(
      long tenantId,
      Long sourceEntityId,
      Long targetEntityId,
      String keyword,
      int pageNum,
      int pageSize) {
    var query = query().where(MetaEntityRelation::getTenantId).eq(tenantId);
    if (sourceEntityId != null) {
      query = query.and(MetaEntityRelation::getSourceEntityId).eq(sourceEntityId);
    }
    if (targetEntityId != null) {
      query = query.and(MetaEntityRelation::getTargetEntityId).eq(targetEntityId);
    }
    if (StringUtils.hasText(keyword)) {
      query = query.and(MetaEntityRelation::getName).like("%" + keyword.trim() + "%");
    }
    return query.orderByDesc(MetaEntityRelation::getId).page(pageNum, pageSize);
  }
}
