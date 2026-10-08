package com.bone.metadata.sdk.support.util;

import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** SQL注入防护工具类 */
public class SqlInjectionPreventer {
  // 聚合表达式模式：可以是聚合函数或字段名
  private static final Pattern SQL_AGGREGATION_PATTERN =
      Pattern.compile(
          "^([a-zA-Z_][a-zA-Z0-9_]*\\([a-zA-Z_*][a-zA-Z0-9_,.*\\s]*\\)|[a-zA-Z_][a-zA-Z0-9_.]*)(\\s+[aA][sS]\\s+[a-zA-Z_][a-zA-Z0-9_]*)?$");

  private static final Pattern SQL_FIELD_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");

  // 更新后的HAVING条件模式，支持聚合函数和别名
  private static final Pattern SQL_HAVING_PATTERN =
      Pattern.compile(
          "^([a-zA-Z_][a-zA-Z0-9_]*\\([a-zA-Z_*][a-zA-Z0-9_,.*\\s]*\\)|[a-zA-Z_][a-zA-Z0-9_.]*)\\s*(=|!=|>|<|>=|<=|\\s+[iI][nN]\\s*\\()\\s*[a-zA-Z0-9_.,'\\s-]+$",
          Pattern.CASE_INSENSITIVE);

  /** 验证并清理聚合表达式 */
  public static String sanitizeAggregation(String aggregation) {
    if (!StringUtils.hasText(aggregation)) {
      throw new IllegalArgumentException("聚合表达式不能为空");
    }

    if (!SQL_AGGREGATION_PATTERN.matcher(aggregation).matches()) {
      throw new IllegalArgumentException("无效的聚合表达式: " + aggregation);
    }

    return aggregation;
  }

  /** 验证并清理字段名 */
  public static String sanitizeFieldName(String fieldName) {
    if (!StringUtils.hasText(fieldName)) {
      throw new IllegalArgumentException("字段名不能为空");
    }

    if (!SQL_FIELD_PATTERN.matcher(fieldName).matches()) {
      throw new IllegalArgumentException("无效的字段名: " + fieldName);
    }

    return fieldName;
  }

  /**
   * 归一化字段引用的表别名限定：先走字段名白名单校验，再剥离调用方可能已写的前缀，最后统一补上目标别名。
   *
   * <p>用于聚合 GROUP BY / SELECT 列表，使 {@code AggregationBuilder} 与 {@code CountAggregationBuilder}
   * 走同一份逻辑， 避免各自实现漂移；剥离再补是为了防止调用方传入 {@code m.category} 时产生 {@code m.m.category} 这种重复限定。
   *
   * @param fieldName 调用方给的字段名（可裸列名，也可自带别名）
   * @param alias 目标表别名（如聚合通道主表别名 {@code m}）
   * @return 形如 {@code m.category} 的限定列名
   */
  public static String qualifyWithAlias(String fieldName, String alias) {
    String safe = sanitizeFieldName(fieldName);
    String bare = safe.contains(".") ? safe.substring(safe.lastIndexOf('.') + 1) : safe;
    return alias + "." + bare;
  }

  /** 验证并清理HAVING条件 */
  public static String sanitizeHavingCondition(String havingCondition) {
    if (!StringUtils.hasText(havingCondition)) {
      throw new IllegalArgumentException("HAVING条件不能为空");
    }

    // 首先尝试匹配聚合函数模式
    if (SQL_AGGREGATION_PATTERN.matcher(havingCondition.split("\\s+")[0]).matches()) {
      // 如果是聚合函数开头的条件，使用更宽松的验证
      Pattern AGGREGATE_HAVING_PATTERN =
          Pattern.compile(
              "^([a-zA-Z_][a-zA-Z0-9_]*\\([a-zA-Z_*][a-zA-Z0-9_,.*\\s]*\\))\\s*(=|!=|>|<|>=|<=)\\s*[a-zA-Z0-9_.,'\\s-]+$",
              Pattern.CASE_INSENSITIVE);

      if (!AGGREGATE_HAVING_PATTERN.matcher(havingCondition).matches()) {
        throw new IllegalArgumentException("无效的HAVING条件: " + havingCondition);
      }
    } else {
      // 对于普通字段条件，使用原有的验证
      if (!SQL_HAVING_PATTERN.matcher(havingCondition).matches()) {
        throw new IllegalArgumentException("无效的HAVING条件: " + havingCondition);
      }
    }

    return havingCondition;
  }
}
