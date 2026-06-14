package com.bone.metadata.sdk.sql.dialect;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import java.util.List;

/** ColumnAllocationDialectFactory 负责根据配置的 DatabaseType 返回对应方言实现。 */
public class ColumnAllocationDialectFactory {

  private final List<ColumnAllocationDialect> dialects;

  public ColumnAllocationDialectFactory(List<ColumnAllocationDialect> dialects) {
    this.dialects = dialects;
  }

  /** 返回与配置 dbType 匹配的 ColumnAllocationDialect 实例 */
  public ColumnAllocationDialect currentDialect() {
    DatabaseType databaseType = MetadataSdkContext.getDatabaseType();
    return dialects.stream()
        .filter(d -> d.getDatabaseType() == databaseType)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unsupported DB type: " + databaseType));
  }
}
