package com.bone.metadata.sdk.sql.dialect;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;

/** 不同数据库的分页语法适配器 */
public class DatabaseDialect {

  /**
   * 构建分页子句
   *
   * @param limit 每页条数
   * @param offset 偏移量
   */
  public String buildPagination(int limit, int offset) {
    if (limit <= 0) return "";
    return switch (MetadataSdkContext.getDatabaseType()) {
      case ORACLE, SQLSERVER -> String.format(
          "OFFSET %d ROWS FETCH NEXT %d ROWS ONLY", offset, limit);
      default -> String.format("LIMIT %d OFFSET %d", limit, offset);
    };
  }

  /** 返回数据库能识别的布尔“假”字面量： H2/MySQL 用 0，Postgres/Oracle 用 FALSE。 */
  public String literalFalse() {
    return switch (MetadataSdkContext.getDatabaseType()) {
      case H2, MYSQL -> "0";
      default -> "FALSE";
    };
  }

  /**
   * 转义标识符（表名/列名），避免 MySQL 保留字（如 {@code condition}）导致 SQL 语法错误。 MySQL/H2 使用反引号，其余数据库使用双引号（SQL
   * 标准）；上下文未初始化时回退反引号， 与 {@code MySQLPlugin} 的 DDL 生成约定一致。
   */
  public String quoteIdentifier(String identifier) {
    DatabaseType type = MetadataSdkContext.peekDatabaseType();
    if (type == null || type == DatabaseType.MYSQL || type == DatabaseType.H2) {
      return "`" + identifier.replace("`", "``") + "`";
    }
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  /** 返回数据库能识别的布尔“假”字面量： H2/MySQL 用 0，Postgres/Oracle 用 TRUE。 */
  public String literalTrue() {
    return switch (MetadataSdkContext.getDatabaseType()) {
      case H2, MYSQL -> "1";
      default -> "TRUE";
    };
  }
}
