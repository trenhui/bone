package com.bone.metadata.sdk.domain.enums;

import java.util.Locale;

public enum DatabaseType {
  MYSQL("mysql"),
  POSTGRESQL("postgresql"),
  ORACLE("oracle"),
  SQLSERVER("sqlserver", "microsoft"),
  DM("dm"),
  OceanBase("oceanbase"),
  H2("h2");

  private final String[] keys;

  DatabaseType(String... keys) {
    this.keys = keys;
  }

  public static DatabaseType fromJdbcUrl(String jdbcUrl) {
    if (jdbcUrl == null || jdbcUrl.isEmpty()) {
      return DatabaseType.MYSQL;
    }
    String lower = jdbcUrl.toLowerCase(Locale.ROOT);
    for (DatabaseType dt : values()) {
      for (String k : dt.keys) {
        if (lower.contains(":" + k + ":")) return dt;
      }
    }
    return DatabaseType.MYSQL;
  }
}
