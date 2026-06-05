package com.bone.metadata.runtime;

import com.bone.metadata.catalog.common.CatalogTenantSupport;
import com.bone.metadata.catalog.domain.enums.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.enums.MetaEntityStatus;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.model.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeFieldColumn;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 从 catalog（meta_*）解析已发布 RUNTIME 实体，供 engine 动态 CRUD 使用 */
@Component
@RequiredArgsConstructor
public class CatalogRuntimeEntityProvider {

  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;

  public Optional<PublishedRuntimeEntity> findPublishedRuntime(String entityCode, long tenantId) {
    List<MetaEntity> found =
        metaEntityRepository
            .query()
            .where(MetaEntity::getTenantId)
            .eq(tenantId)
            .and(MetaEntity::getCode)
            .eq(entityCode)
            .list();
    if (found.isEmpty()) {
      return Optional.empty();
    }
    MetaEntity entity = found.get(0);
    if (entity.statusEnum() != MetaEntityStatus.PUBLISHED) {
      return Optional.empty();
    }
    if (entity.deliveryModeEnum() != MetaDeliveryMode.RUNTIME) {
      return Optional.empty();
    }

    List<MetaField> fields =
        metaFieldRepository
            .query()
            .where(MetaField::getEntityId)
            .eq(entity.getId())
            .orderByAsc(MetaField::getSortOrder)
            .list();

    String pk = "id";
    List<RuntimeFieldColumn> columns = new ArrayList<>();
    for (MetaField f : fields) {
      if (Boolean.TRUE.equals(f.getPk())) {
        pk = f.getCode();
      }
      columns.add(
          new RuntimeFieldColumn(
              f.getCode(), Boolean.TRUE.equals(f.getRequired()), Boolean.TRUE.equals(f.getPk())));
    }

    return Optional.of(
        new PublishedRuntimeEntity(
            entity.getCode(), entity.getTableName(), pk, tenantId, columns));
  }

  /** 供 Controller 默认租户 */
  public Optional<PublishedRuntimeEntity> findPublishedRuntime(String entityCode) {
    return findPublishedRuntime(entityCode, CatalogTenantSupport.currentTenantId());
  }
}
