package com.bone.masterdata.domain.gateway;

import java.util.List;

/** 读取 catalog {@code meta_entity}（MD-04）出站端口。 */
public interface MetaEntityCatalogPort {

  int META_STATUS_PUBLISHED = 1;

  MetaEntityRow requirePublished(Long metaEntityId);

  /** 读取元数据实体字段集（UC-T10 模型漂移对账的数据源）。 */
  List<MetaFieldRow> requireFields(Long metaEntityId);

  record MetaEntityRow(Long id, String code, String displayName, int status) {}

  /** 元数据字段行：type 为元数据类型原值（STRING/NUMBER/...）。 */
  record MetaFieldRow(String code, String displayName, String type, boolean required) {}
}
