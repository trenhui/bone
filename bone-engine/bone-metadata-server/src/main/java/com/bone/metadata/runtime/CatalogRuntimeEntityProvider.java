package com.bone.metadata.runtime;

import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaEntityStatus;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeFieldColumn;
import com.bone.metadata.runtime.application.RuntimeActionDefRepository;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 从 catalog（meta_*）解析已发布 RUNTIME 实体，供 engine 动态 CRUD 使用 */
@Component
@RequiredArgsConstructor
public class CatalogRuntimeEntityProvider {

  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;
  private final TenantProvider tenantProvider;
  private final RuntimeActionDefRepository actionDefRepository;

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
              f.getCode(),
              f.getType(),
              Boolean.TRUE.equals(f.getRequired()),
              Boolean.TRUE.equals(f.getUnique()),
              Boolean.TRUE.equals(f.getPk()),
              f.getPhysicalColumn()));
    }

    // G14：state_field 从动作定义推导（零 DDL 过渡方案，Runtime 设计 §4.5.1）——
    // 同一实体全部动作的 status_field 必须一致（种子由迁移保证），任取其一即可。
    String stateField =
        actionDefRepository.listByEntity(tenantId, entity.getCode()).stream()
            .map(RuntimeActionDef::getStatusField)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    return Optional.of(
        new PublishedRuntimeEntity(
            entity.getCode(), entity.getTableName(), pk, tenantId, columns, stateField));
  }

  /** 供 Controller 默认租户 */
  public Optional<PublishedRuntimeEntity> findPublishedRuntime(String entityCode) {
    return findPublishedRuntime(entityCode, tenantProvider.currentTenantId());
  }
}
