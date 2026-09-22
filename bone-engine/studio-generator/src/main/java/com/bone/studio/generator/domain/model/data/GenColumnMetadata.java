package com.bone.studio.generator.domain.model.data;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Table("gen_column_metadata")
public class GenColumnMetadata extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private Long tenantId;
  private Long tableMetadataId;
  private String originalColumnName;
  private String customFieldName;
  private String jdbcType;
  private String javaType;
  private String columnType;
  private Integer columnLength;
  private Integer precisionValue;
  private Integer scaleValue;
  private boolean isNullable;
  private boolean isPrimaryKey;
  private boolean isAutoincrement;
  private String defaultValue;
  private String columnComment;
  private int sortOrder;
  private Long createdBy;
  private Long updatedBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private boolean deleted;

  private GenColumnMetadata() {}

  public static GenColumnMetadata create(
      Long id, Long tenantId, Long tableMetadataId, TableColumn dbColumn) {
    GenColumnMetadata metadata = new GenColumnMetadata();
    metadata.id = id;
    metadata.tenantId = tenantId;
    metadata.tableMetadataId = tableMetadataId;
    metadata.originalColumnName = dbColumn.getColumnName();
    metadata.customFieldName = dbColumn.getColumnName();
    metadata.jdbcType = String.valueOf(dbColumn.getJdbcType());
    metadata.javaType = mapJdbcTypeToJavaType(dbColumn.getJdbcType());
    metadata.columnType = dbColumn.getColumnType();
    metadata.columnLength = dbColumn.getColumnSize();
    metadata.precisionValue = dbColumn.getColumnSize();
    metadata.scaleValue = dbColumn.getDecimalDigits();
    metadata.isNullable = dbColumn.isNullable();
    metadata.isPrimaryKey = false; // 需要单独判断
    metadata.isAutoincrement = false; // 需要单独判断
    metadata.defaultValue = null;
    metadata.columnComment = dbColumn.getColumnComment();
    metadata.sortOrder = 0;
    metadata.createdAt = LocalDateTime.now();
    metadata.updatedAt = LocalDateTime.now();
    metadata.deleted = false;
    return metadata;
  }

  private static String mapJdbcTypeToJavaType(int jdbcType) {
    switch (jdbcType) {
      case java.sql.Types.VARCHAR:
      case java.sql.Types.CHAR:
      case -1: // CLOB/TEXT type
        return "String";
      case java.sql.Types.INTEGER:
      case java.sql.Types.SMALLINT:
        return "Integer";
      case java.sql.Types.BIGINT:
        return "Long";
      case java.sql.Types.FLOAT:
        return "Float";
      case java.sql.Types.DOUBLE:
        return "Double";
      case java.sql.Types.DECIMAL:
        return "BigDecimal";
      case java.sql.Types.DATE:
        return "LocalDate";
      case java.sql.Types.TIMESTAMP:
        return "LocalDateTime";
      case java.sql.Types.BOOLEAN:
        return "Boolean";
      default:
        return "String";
    }
  }

  public static class Builder {
    private Long id;
    private Long tenantId;
    private Long tableMetadataId;
    private String originalColumnName;
    private String customFieldName;
    private String jdbcType;
    private String javaType;
    private String columnType;
    private Integer columnLength;
    private Integer precisionValue;
    private Integer scaleValue;
    private boolean isNullable;
    private boolean isPrimaryKey;
    private boolean isAutoincrement;
    private String defaultValue;
    private String columnComment;
    private int sortOrder;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(Long tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder tableMetadataId(Long tableMetadataId) {
      this.tableMetadataId = tableMetadataId;
      return this;
    }

    public Builder originalColumnName(String originalColumnName) {
      this.originalColumnName = originalColumnName;
      return this;
    }

    public Builder customFieldName(String customFieldName) {
      this.customFieldName = customFieldName;
      return this;
    }

    public Builder jdbcType(String jdbcType) {
      this.jdbcType = jdbcType;
      return this;
    }

    public Builder javaType(String javaType) {
      this.javaType = javaType;
      return this;
    }

    public Builder columnType(String columnType) {
      this.columnType = columnType;
      return this;
    }

    public Builder columnLength(Integer columnLength) {
      this.columnLength = columnLength;
      return this;
    }

    public Builder precisionValue(Integer precisionValue) {
      this.precisionValue = precisionValue;
      return this;
    }

    public Builder scaleValue(Integer scaleValue) {
      this.scaleValue = scaleValue;
      return this;
    }

    public Builder isNullable(boolean isNullable) {
      this.isNullable = isNullable;
      return this;
    }

    public Builder isPrimaryKey(boolean isPrimaryKey) {
      this.isPrimaryKey = isPrimaryKey;
      return this;
    }

    public Builder isAutoincrement(boolean isAutoincrement) {
      this.isAutoincrement = isAutoincrement;
      return this;
    }

    public Builder defaultValue(String defaultValue) {
      this.defaultValue = defaultValue;
      return this;
    }

    public Builder columnComment(String columnComment) {
      this.columnComment = columnComment;
      return this;
    }

    public Builder sortOrder(int sortOrder) {
      this.sortOrder = sortOrder;
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

    public GenColumnMetadata build() {
      GenColumnMetadata metadata = new GenColumnMetadata();
      metadata.id = id;
      metadata.tenantId = tenantId;
      metadata.tableMetadataId = tableMetadataId;
      metadata.originalColumnName = originalColumnName;
      metadata.customFieldName = customFieldName;
      metadata.jdbcType = jdbcType;
      metadata.javaType = javaType;
      metadata.columnType = columnType;
      metadata.columnLength = columnLength;
      metadata.precisionValue = precisionValue;
      metadata.scaleValue = scaleValue;
      metadata.isNullable = isNullable;
      metadata.isPrimaryKey = isPrimaryKey;
      metadata.isAutoincrement = isAutoincrement;
      metadata.defaultValue = defaultValue;
      metadata.columnComment = columnComment;
      metadata.sortOrder = sortOrder;
      metadata.createdBy = createdBy;
      metadata.updatedBy = updatedBy;
      metadata.createdAt = createdAt;
      metadata.updatedAt = updatedAt;
      metadata.deleted = deleted;
      return metadata;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
