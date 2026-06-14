package com.bone.metadata.sdk.sql.processor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 封装处理后的 SQL 语句和参数，确保结果不可变。 */
public class ProcessedSql {
  private final String sql;
  private final Map<String, Object> effectiveParams;

  /**
   * 构造函数。
   *
   * @param sql 处理后的 SQL 语句
   * @param effectiveParams 处理后的参数映射
   */
  public ProcessedSql(String sql, Map<String, Object> effectiveParams) {
    this.sql = Objects.requireNonNull(sql, "SQL must not be null");
    this.effectiveParams = Collections.unmodifiableMap(new HashMap<>(effectiveParams));
  }

  public String getSql() {
    return sql;
  }

  public Map<String, Object> getEffectiveParams() {
    return effectiveParams;
  }
}
