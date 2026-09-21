package com.bone.metadata.catalog.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.domain.model.MetaField;
import com.bone.metadata.sdk.Repository;
import java.util.Optional;
import org.springframework.util.StringUtils;

/** 字段聚合仓储。读模型方法（ADR-0030）下沉至此；应用层调用而非持有读侧 DSL。 */
public interface MetaFieldRepository extends Repository<MetaField, Long> {

  default PageResult<MetaField> pageFields(
      Long entityId, String keyword, int pageNum, int pageSize) {
    var query = query();
    if (entityId != null) {
      query = query.where(MetaField::getEntityId).eq(entityId);
    }
    if (StringUtils.hasText(keyword)) {
      if (entityId != null) {
        query = query.and(MetaField::getName).like("%" + keyword.trim() + "%");
      } else {
        query = query.where(MetaField::getName).like("%" + keyword.trim() + "%");
      }
    }
    return query.orderByAsc(MetaField::getSortOrder).page(pageNum, pageSize);
  }

  default Optional<MetaField> findByEntityAndCode(Long entityId, String code) {
    return where(MetaField::getEntityId).eq(entityId).and(MetaField::getCode).eq(code).first();
  }
}
