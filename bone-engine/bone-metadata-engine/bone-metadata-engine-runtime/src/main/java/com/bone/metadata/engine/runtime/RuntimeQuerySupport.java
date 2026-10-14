package com.bone.metadata.engine.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** 将 {@link RuntimePageQuery} 约束到 catalog 列白名单。 */
final class RuntimeQuerySupport {

  private RuntimeQuerySupport() {}

  static List<String> resolveSelectColumns(PublishedRuntimeEntity entity, RuntimePageQuery query) {
    Set<String> allowed = allowedDataColumns(entity);
    String pk = entity.primaryKeyColumn();
    if (query == null || query.selectFields() == null || query.selectFields().isEmpty()) {
      return List.of("*");
    }
    LinkedHashSet<String> cols = new LinkedHashSet<>();
    for (String field : query.selectFields()) {
      String code = sanitize(field);
      if (!allowed.contains(code) && !code.equals(pk)) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_INVALID_QUERY, "fields 含未建模列: " + field);
      }
      cols.add(physicalColumnOf(entity, code));
    }
    cols.add(physicalColumnOf(entity, pk));
    if (entity.hasColumn("tenant_id")) {
      cols.add("tenant_id");
    }
    return cols.stream().map(c -> "`" + c + "`").collect(Collectors.toList());
  }

  static String buildOrderBy(PublishedRuntimeEntity entity, RuntimePageQuery query) {
    Set<String> allowed = allowedDataColumns(entity);
    String pk = entity.primaryKeyColumn();
    if (query == null || query.sortSpecs() == null || query.sortSpecs().isEmpty()) {
      return " ORDER BY `" + sanitize(pk) + "` DESC";
    }
    List<String> parts = new ArrayList<>();
    for (RuntimePageQuery.SortSpec spec : query.sortSpecs()) {
      String field = sanitize(spec.field());
      if (!allowed.contains(field) && !field.equals(pk)) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_INVALID_QUERY, "sort 含未建模列: " + spec.field());
      }
      parts.add(
          "`" + physicalColumnOf(entity, field) + "` " + (spec.descending() ? "DESC" : "ASC"));
    }
    return " ORDER BY " + String.join(", ", parts);
  }

  /**
   * 多条件过滤子句（AND 语义，P1-3 运算符扩展集）。
   *
   * <p>参数名带索引（{@code qFilterValue0/1/2…}而非复用同一个名字）：{@code MapSqlParameterSource} 同名 addValue
   * 是覆盖而非追加，复用名字会让「最后一个条件的值」填充全部占位符—— 表现为多条件筛选 结果恒等于最后一个条件（静默错误结果，比报错危险得多）。
   *
   * <p>LIKE 通配符转义（{@code contains}）：值里的 {@code %} / {@code _} / {@code \} 必须转义—— 否则用户搜 {@code 50%}
   * 会匹配「含 50 的任意串」（静默扩大结果集），搜 {@code _} 匹配任意单字符。 MySQL LIKE 默认转义符即反斜杠，参数化传转义后的值即可，无需显式 ESCAPE 子句。
   *
   * <p>IN 拆分（{@code in}）：值按逗号拆分为多参数（{@code qFilterValue{i}_0/1…}）；空元素保留为空串（用户可能就是要匹配空值列）。
   */
  static String buildFilterClause(
      PublishedRuntimeEntity entity, RuntimePageQuery query, MapSqlParameterSource params) {
    if (query == null || query.filters() == null || query.filters().isEmpty()) {
      return "";
    }
    Set<String> allowed = allowedDataColumns(entity);
    StringBuilder clause = new StringBuilder();
    for (int i = 0; i < query.filters().size(); i++) {
      RuntimePageQuery.FilterSpec filter = query.filters().get(i);
      String field = sanitize(filter.field());
      if (!allowed.contains(field)) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_INVALID_QUERY, "q 过滤字段未建模: " + filter.field());
      }
      String column = "`" + physicalColumnOf(entity, field) + "`";
      String valueKey = "qFilterValue" + i;
      switch (filter.op()) {
        case EQ -> appendComparison(clause, column, "=", valueKey, params, filter.value());
        case NE -> appendComparison(clause, column, "<>", valueKey, params, filter.value());
        case GT -> appendComparison(clause, column, ">", valueKey, params, filter.value());
        case GTE -> appendComparison(clause, column, ">=", valueKey, params, filter.value());
        case LT -> appendComparison(clause, column, "<", valueKey, params, filter.value());
        case LTE -> appendComparison(clause, column, "<=", valueKey, params, filter.value());
        case CONTAINS -> {
          params.addValue(valueKey, "%" + escapeLike(filter.value()) + "%");
          clause.append(" AND ").append(column).append(" LIKE :").append(valueKey);
        }
        case IN -> {
          String[] parts = filter.value().split(",");
          String[] placeholders = new String[parts.length];
          for (int j = 0; j < parts.length; j++) {
            String partKey = valueKey + "_" + j;
            params.addValue(partKey, parts[j].trim());
            placeholders[j] = ":" + partKey;
          }
          clause
              .append(" AND ")
              .append(column)
              .append(" IN (")
              .append(String.join(", ", placeholders))
              .append(")");
        }
        default -> throw new IllegalStateException("未覆盖的运算符: " + filter.op());
      }
    }
    return clause.toString();
  }

  private static void appendComparison(
      StringBuilder clause,
      String column,
      String sqlOp,
      String valueKey,
      MapSqlParameterSource params,
      String value) {
    params.addValue(valueKey, value);
    clause.append(" AND ").append(column).append(' ').append(sqlOp).append(" :").append(valueKey);
  }

  /**
   * LIKE 模式转义：{@code \} → {@code \\}，{@code %} → {@code \%}，{@code _} → {@code \_}。
   *
   * <p>顺序要紧：反斜杠必须第一个替换，否则后两步生成的转义符会被二次转义。
   */
  static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Set<String> allowedDataColumns(PublishedRuntimeEntity entity) {
    return entity.columns().stream().map(RuntimeFieldColumn::code).collect(Collectors.toSet());
  }

  /** 物理列名解析：机制 B 返回 ext_* 预留列，机制 A 返回 code 本身（用于 SELECT/ORDER/FILTER 渲染）。 */
  private static String physicalColumnOf(PublishedRuntimeEntity entity, String code) {
    return entity.columns().stream()
        .filter(c -> c.code().equals(code))
        .findFirst()
        .map(c -> c.physicalColumn() != null ? c.physicalColumn() : code)
        .orElse(code);
  }

  private static String sanitize(String name) {
    if (name == null || !name.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_INVALID_IDENTIFIER, "非法标识符: " + name);
    }
    return name;
  }
}
