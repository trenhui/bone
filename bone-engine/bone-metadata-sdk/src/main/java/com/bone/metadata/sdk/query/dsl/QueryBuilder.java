package com.bone.metadata.sdk.query.dsl;

import com.bone.core.annotation.ReadSideOnly;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import java.util.Objects;

/** 查询构建器工厂 - 创建类型安全的流畅查询 */
@ReadSideOnly
public class QueryBuilder {

  private static SqlExecutor sqlExecutor;

  private QueryBuilder() {}

  public static void initialize(SqlExecutor sqlExecutor) {
    QueryBuilder.sqlExecutor = Objects.requireNonNull(sqlExecutor, "SqlExecutor must not be null");
  }

  // 提供一个 getter 方便调试
  private static SqlExecutor getSqlExecutor() {
    if (sqlExecutor == null) {
      throw new IllegalStateException(
          "QueryBuilder not initialized. Call QueryBuilder.initialize() first.");
    }
    return sqlExecutor;
  }

  /** 为实体类创建查询 */
  public static <T> FluentQuery<T> from(Class<T> entityClass) {
    return new DefaultFluentQuery<>(entityClass, getSqlExecutor(), "t");
  }

  /** 为实体类创建查询（指定别名） */
  public static <T> FluentQuery<T> from(Class<T> entityClass, String alias) {
    return new DefaultFluentQuery<>(entityClass, getSqlExecutor(), alias);
  }
}
