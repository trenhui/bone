package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.domain.model.TableMetadata;

public class DynamicUpdateContext {
  private final TableMetadata table;
  private final Object entity;

  public DynamicUpdateContext(TableMetadata table, Object entity) {
    this.table = table;
    this.entity = entity;
  }

  public TableMetadata getTable() {
    return table;
  }

  public Object getEntity() {
    return entity;
  }
}
