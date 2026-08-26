package com.bone.metadata.engine.starter.platform;

import com.bone.metadata.engine.ports.spi.MetadataPlatformBridge;
import com.bone.metadata.engine.starter.platform.model.PlatformMetaEntity;
import com.bone.metadata.engine.starter.platform.model.PlatformMetaField;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 基于 bone-metadata-sdk 查询 meta_* 已发布实体（status=1），供 metadata-engine 消费。 */
public class SdkMetadataPlatformBridge implements MetadataPlatformBridge {

  private static final Logger log = LoggerFactory.getLogger(SdkMetadataPlatformBridge.class);
  private static final int PUBLISHED = 1;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Optional<String> loadPublishedEntityJson(Long tenantId, String entityCode) {
    if (entityCode == null || entityCode.isBlank()) {
      return Optional.empty();
    }
    long tid = tenantId != null ? tenantId : 0L;
    try {
      Optional<PlatformMetaEntity> entityOpt =
          QueryBuilder.from(PlatformMetaEntity.class)
              .where(PlatformMetaEntity::getTenantId)
              .eq(tid)
              .and(PlatformMetaEntity::getCode)
              .eq(entityCode.trim())
              .and(PlatformMetaEntity::getStatus)
              .eq(PUBLISHED)
              .first();
      if (entityOpt.isEmpty()) {
        return Optional.empty();
      }
      PlatformMetaEntity entity = entityOpt.get();
      List<PlatformMetaField> fields =
          QueryBuilder.from(PlatformMetaField.class)
              .where(PlatformMetaField::getEntityId)
              .eq(entity.getId())
              .orderByAsc(PlatformMetaField::getSortOrder)
              .list();
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("entity", entityPayload(entity));
      List<Map<String, Object>> fieldList = new ArrayList<>();
      for (PlatformMetaField field : fields) {
        fieldList.add(fieldPayload(field));
      }
      snapshot.put("fields", fieldList);
      return Optional.of(MAPPER.writeValueAsString(snapshot));
    } catch (Exception e) {
      log.warn("加载 meta 快照失败 tenantId={} code={}: {}", tid, entityCode, e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> currentTenantId() {
    // SdkMetadataPlatformBridge 无租户上下文来源，返回空；接真实现见 runtime.adapter.IamMetadataBridge。
    return Optional.empty();
  }

  @Override
  public void publishEvent(String eventJson) {
    log.debug("SdkMetadataPlatformBridge publishEvent: {}", eventJson);
  }

  private static Map<String, Object> entityPayload(PlatformMetaEntity entity) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", entity.getId());
    map.put("code", entity.getCode());
    map.put("displayName", entity.getDisplayName());
    map.put("tableName", entity.getTableName());
    map.put("status", entity.getStatus());
    return map;
  }

  private static Map<String, Object> fieldPayload(PlatformMetaField field) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("code", field.getCode());
    map.put("displayName", field.getDisplayName());
    map.put("type", field.getType());
    map.put("required", field.getRequired());
    map.put("pk", field.getPk());
    map.put("sortOrder", field.getSortOrder());
    return map;
  }
}
