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
        throw new RuntimeRecordException("META_RUNTIME_INVALID_QUERY", "fields 含未建模列: " + field);
      }
      cols.add(code);
    }
    cols.add(pk);
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
            "META_RUNTIME_INVALID_QUERY", "sort 含未建模列: " + spec.field());
      }
      parts.add("`" + field + "` " + (spec.descending() ? "DESC" : "ASC"));
    }
    return " ORDER BY " + String.join(", ", parts);
  }

  static String buildFilterClause(
      PublishedRuntimeEntity entity, RuntimePageQuery query, MapSqlParameterSource params) {
    if (query == null || query.filter() == null) {
      return "";
    }
    RuntimePageQuery.FilterSpec filter = query.filter();
    String field = sanitize(filter.field());
    Set<String> allowed = allowedDataColumns(entity);
    if (!allowed.contains(field)) {
      throw new RuntimeRecordException(
          "META_RUNTIME_INVALID_QUERY", "q 过滤字段未建模: " + filter.field());
    }
    params.addValue("qFilterValue", filter.value());
    return " AND `" + field + "` = :qFilterValue";
  }

  private static Set<String> allowedDataColumns(PublishedRuntimeEntity entity) {
    return entity.columns().stream().map(RuntimeFieldColumn::code).collect(Collectors.toSet());
  }

  private static String sanitize(String name) {
    if (name == null || !name.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
      throw new RuntimeRecordException("META_RUNTIME_INVALID_IDENTIFIER", "非法标识符: " + name);
    }
    return name;
  }
}
