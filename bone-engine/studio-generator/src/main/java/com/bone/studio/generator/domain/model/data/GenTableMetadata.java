package com.bone.studio.generator.domain.model.data;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import java.util.List;

@Table("gen_table_metadata")
public class GenTableMetadata extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long tenantId;
  private String dataSourceId;
  private String tableSchema;
  private String originalTableName;
  private String customEntityName;
  private String moduleName;
  private String tableComment;
  private String syncStatus;
  private LocalDateTime lastSyncAt;
  private Long createdBy;
  private Long updatedBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private boolean deleted;
  private int version;
  private List<GenColumnMetadata> columns;

  private GenTableMetadata() {}

  public static GenTableMetadata create(
      Long id, Long tenantId, String dataSourceId, DatabaseTable dbTable) {
    GenTableMetadata metadata = new GenTableMetadata();
    metadata.id = id;
    metadata.tenantId = tenantId;
    metadata.dataSourceId = dataSourceId;
    metadata.originalTableName = dbTable.getTableName();
    metadata.customEntityName = dbTable.getTableName();
    metadata.tableComment = dbTable.getTableComment();
    metadata.syncStatus = "SYNCED";
    metadata.lastSyncAt = LocalDateTime.now();
    metadata.createdAt = LocalDateTime.now();
    metadata.updatedAt = LocalDateTime.now();
    metadata.deleted = false;
    metadata.version = 0;
    return metadata;
  }

  public void syncColumns(List<TableColumn> dbColumns) {
    // 同步列信息的逻辑
  }

  public Long getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public String getDataSourceId() {
    return dataSourceId;
  }

  public String getTableSchema() {
    return tableSchema;
  }

  public String getOriginalTableName() {
    return originalTableName;
  }

  public String getCustomEntityName() {
    return customEntityName;
  }

  public String getModuleName() {
    return moduleName;
  }

  public String getTableComment() {
    return tableComment;
  }

  public String getSyncStatus() {
    return syncStatus;
  }

  public LocalDateTime getLastSyncAt() {
    return lastSyncAt;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public Long getUpdatedBy() {
    return updatedBy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public int getVersion() {
    return version;
  }

  public List<GenColumnMetadata> getColumns() {
    return columns;
  }

  public static class Builder {
    private Long id;
    private Long tenantId;
    private String dataSourceId;
    private String tableSchema;
    private String originalTableName;
    private String customEntityName;
    private String moduleName;
    private String tableComment;
    private String syncStatus;
    private LocalDateTime lastSyncAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;
    private int version;
    private List<GenColumnMetadata> columns;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(Long tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder dataSourceId(String dataSourceId) {
      this.dataSourceId = dataSourceId;
      return this;
    }

    public Builder tableSchema(String tableSchema) {
      this.tableSchema = tableSchema;
      return this;
    }

    public Builder originalTableName(String originalTableName) {
      this.originalTableName = originalTableName;
      return this;
    }

    public Builder customEntityName(String customEntityName) {
      this.customEntityName = customEntityName;
      return this;
    }

    public Builder moduleName(String moduleName) {
      this.moduleName = moduleName;
      return this;
    }

    public Builder tableComment(String tableComment) {
      this.tableComment = tableComment;
      return this;
    }

    public Builder syncStatus(String syncStatus) {
      this.syncStatus = syncStatus;
      return this;
    }

    public Builder lastSyncAt(LocalDateTime lastSyncAt) {
      this.lastSyncAt = lastSyncAt;
      return this;
    }

    public Builder createdBy(Long createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder updatedBy(Long updatedBy) {
      this.updatedBy = updatedBy;
      return this;
    }

    public Builder createdAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder updatedAt(LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public Builder deleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public Builder version(int version) {
      this.version = version;
      return this;
    }

    public Builder columns(List<GenColumnMetadata> columns) {
      this.columns = columns;
      return this;
    }

    public GenTableMetadata build() {
      GenTableMetadata metadata = new GenTableMetadata();
      metadata.id = id;
      metadata.tenantId = tenantId;
      metadata.dataSourceId = dataSourceId;
      metadata.tableSchema = tableSchema;
      metadata.originalTableName = originalTableName;
      metadata.customEntityName = customEntityName;
      metadata.moduleName = moduleName;
      metadata.tableComment = tableComment;
      metadata.syncStatus = syncStatus;
      metadata.lastSyncAt = lastSyncAt;
      metadata.createdBy = createdBy;
      metadata.updatedBy = updatedBy;
      metadata.createdAt = createdAt;
      metadata.updatedAt = updatedAt;
      metadata.deleted = deleted;
      metadata.version = version;
      metadata.columns = columns;
      return metadata;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
