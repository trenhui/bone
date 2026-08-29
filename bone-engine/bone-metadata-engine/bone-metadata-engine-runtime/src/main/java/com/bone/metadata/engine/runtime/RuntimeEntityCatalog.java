package com.bone.metadata.engine.runtime;

import java.util.Optional;

/** 解析已发布 RUNTIME 实体（由宿主应用实现，如 bone-metadata-server） */
public interface RuntimeEntityCatalog {

  Optional<PublishedRuntimeEntity> findPublishedRuntime(String entityCode, long tenantId);
}
