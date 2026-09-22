package com.bone.metadata.sdk.domain.model;

import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

@Data
public class TableMetadata {
  private final String name; // 表名
  private final List<ColumnMetadata> columns; // 表的列元数据
  private final List<CascadeRelation> cascades; // @Cascade 子集合（可为空）

  @Getter(AccessLevel.NONE)
  private final ColumnMetadata primaryKey; // 缓存的主键列

  private final ColumnMetadata version; // 缓存版本列

  @Getter(AccessLevel.NONE)
  private final ColumnMetadata softDeleted; // 缓存软删列

  @Getter(AccessLevel.NONE)
  private final ColumnMetadata tenantIdColumn; // 缓存租户列（ADR-0029）

  private ExtensionMode extensionMode = ExtensionMode.RESERVED_COLUMNS;

  public TableMetadata(String name, List<ColumnMetadata> columns) {
    this(name, columns, List.of());
  }

  public TableMetadata(String name, List<ColumnMetadata> columns, List<CascadeRelation> cascades) {
    this.name = name;
    this.columns = columns;
    this.cascades = cascades == null ? List.of() : List.copyOf(cascades);
    this.primaryKey =
        columns.stream()
            .filter(ColumnMetadata::isPrimaryKey)
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("No primary key found for table " + name));

    this.version = columns.stream().filter(ColumnMetadata::isVersion).findFirst().orElse(null);

    this.softDeleted =
        columns.stream().filter(ColumnMetadata::isSoftDeleted).findFirst().orElse(null);

    // 租户表识别：Java 字段名 tenantId，或物理列名 tenant_id / tenantId（ADR-0029 复核修订：避免 camelCase / 自有字段漏判）
    this.tenantIdColumn =
        columns.stream()
            .filter(
                c ->
                    "tenantId".equals(c.getFieldName())
                        || "tenant_id".equals(c.getName())
                        || "tenantId".equals(c.getName()))
            .findFirst()
            .orElse(null);
  }

  public ColumnMetadata getPrimaryKey() {
    if (primaryKey == null) {
      throw new IllegalStateException(String.format("表 %s 未定义主键列", name));
    }
    return primaryKey;
  }

  public ColumnMetadata getTenantIdColumn() {
    return tenantIdColumn;
  }

  public boolean isTenantScoped() {
    return tenantIdColumn != null;
  }

  /** 该表是否启用 SDK 原生乐观锁：存在 {@code @Version} 列即启用（ADR-0031 D1）。 */
  public boolean isVersioned() {
    return version != null;
  }

  public boolean isSoftDeletable() {
    return softDeleted != null;
  }
}
