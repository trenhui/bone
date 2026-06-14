package com.bone.masterdata.domain.gateway;

/** 读取 catalog {@code meta_entity}（MD-04）出站端口。 */
public interface MetaEntityCatalogPort {

  int META_STATUS_PUBLISHED = 1;

  MetaEntityRow requirePublished(Long metaEntityId);

  record MetaEntityRow(Long id, String code, String displayName, int status) {}
}
