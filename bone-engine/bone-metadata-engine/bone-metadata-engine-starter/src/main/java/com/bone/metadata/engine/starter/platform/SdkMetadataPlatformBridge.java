package com.bone.metadata.engine.starter.platform;

import com.bone.metadata.engine.ports.spi.MetadataPlatformBridge;
import com.bone.metadata.engine.runtime.adapter.po.MetaEntityPo;
import com.bone.metadata.engine.runtime.adapter.po.MetaFieldPo;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 bone-metadata-sdk 查询 meta_* 已发布实体（status=1），供 metadata-engine 消费。
 *
 * <p>复用 runtime 的 {@code MetaEntityPo}/{@code MetaFieldPo}，不另维护第二份同表映射（此前 {@code platform.model}
 * 下的重复映射已删除，见列契约测试 {@code MetaEntityPoColumnContractTest}）。
 */
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
      Optional<MetaEntityPo> entityOpt =
          QueryBuilder.from(MetaEntityPo.class)
              .where(MetaEntityPo::getTenantId)
              .eq(tid)
              .and(MetaEntityPo::getCode)
              .eq(entityCode.trim())
              .and(MetaEntityPo::getStatus)
              .eq(PUBLISHED)
              .first();
      if (entityOpt.isEmpty()) {
        return Optional.empty();
      }
      MetaEntityPo entity = entityOpt.get();
      List<MetaFieldPo> fields =
          QueryBuilder.from(MetaFieldPo.class)
              .where(MetaFieldPo::getEntityId)
              .eq(entity.getId())
              .orderByAsc(MetaFieldPo::getSortOrder)
              .list();
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("entity", entityPayload(entity));
      List<Map<String, Object>> fieldList = new ArrayList<>();
      for (MetaFieldPo field : fields) {
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

  private static Map<String, Object> entityPayload(MetaEntityPo entity) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", entity.getId());
    map.put("code", entity.getCode());
    map.put("displayName", entity.getDisplayName());
    map.put("tableName", entity.getTableName());
    map.put("status", entity.getStatus());
    return map;
  }

  private static Map<String, Object> fieldPayload(MetaFieldPo field) {
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
