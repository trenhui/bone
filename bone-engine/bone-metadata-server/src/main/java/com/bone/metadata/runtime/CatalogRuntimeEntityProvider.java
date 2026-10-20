package com.bone.metadata.runtime;

import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaEntityStatus;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.repository.UiSchemaDefRepository;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeFieldColumn;
import com.bone.metadata.engine.runtime.RuntimeFieldVisibility;
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
  private final UiSchemaDefRepository uiSchemaDefRepository;
  private final RuntimeFieldVisibilityProjector fieldVisibilityProjector;

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
              f.getPhysicalColumn(),
              f.getEnumValues(),
              f.getValidationRules()));
    }

    // G14：state_field 从动作定义推导（零 DDL 过渡方案，Runtime 设计 §4.5.1）——
    // 同一实体全部动作的 status_field 必须一致（种子由迁移保证），任取其一即可。
    String stateField =
        actionDefRepository.listByEntity(tenantId, entity.getCode()).stream()
            .map(RuntimeActionDef::getStatusField)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    // 字段级角色可见性：从发布态 ui-schema 投影（N7 服务端闭环）。
    // 与 schemaForExecution 同口径——publishedSchemaJson 优先，缺失回落 schemaJson。
    List<RuntimeFieldVisibility> fieldVisibilities =
        fieldVisibilityProjector.project(publishedUiSchemaJson(entity));
    return Optional.of(
        new PublishedRuntimeEntity(
            entity.getCode(),
            entity.getTableName(),
            pk,
            tenantId,
            columns,
            stateField,
            entity.getRowPolicies(),
            entity.getAutoNumber(),
            fieldVisibilities));
  }

  /** 实体的界面 schema JSON（发布态优先，草稿态回落）；未配置返回 null（= 无字段级限制）。 */
  private String publishedUiSchemaJson(MetaEntity entity) {
    return uiSchemaDefRepository
        .findByTenantAndEntity(entity.getTenantId(), entity.getId())
        .map(
            def -> {
              String json = def.getPublishedSchemaJson();
              return json == null || json.isBlank() ? def.getSchemaJson() : json;
            })
        .filter(json -> json != null && !json.isBlank())
        .orElse(null);
  }

  /** 供 Controller 默认租户 */
  public Optional<PublishedRuntimeEntity> findPublishedRuntime(String entityCode) {
    return findPublishedRuntime(entityCode, tenantProvider.currentTenantId());
  }
}
