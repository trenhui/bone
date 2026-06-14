package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.domain.model.TableMetadata;

public class UpsertContext {
  private final TableMetadata table;
  private final Object entity;
  private final DatabaseType dbType;

  public UpsertContext(TableMetadata table, Object entity, DatabaseType dbType) {
    this.table = table;
    this.entity = entity;
    this.dbType = dbType;
  }

  public TableMetadata getTable() {
    return table;
  }

  public Object getEntity() {
    return entity;
  }

  public DatabaseType getDbType() {
    return dbType;
  }
}
