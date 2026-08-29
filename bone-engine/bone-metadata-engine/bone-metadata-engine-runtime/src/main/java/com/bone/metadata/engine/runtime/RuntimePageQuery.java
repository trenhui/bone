package com.bone.metadata.engine.runtime;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/** 模式 B 列表查询参数（fields / sort / q）。 */
public record RuntimePageQuery(
    List<String> selectFields, List<SortSpec> sortSpecs, FilterSpec filter) {

  public static final RuntimePageQuery EMPTY = new RuntimePageQuery(null, null, null);

  public static RuntimePageQuery parse(String fields, String sort, String q) {
    List<String> select = parseFields(fields);
    List<SortSpec> sorts = parseSort(sort);
    FilterSpec filter = parseFilter(q);
    if (select.isEmpty() && sorts.isEmpty() && filter == null) {
      return EMPTY;
    }
    return new RuntimePageQuery(
        select.isEmpty() ? null : select, sorts.isEmpty() ? null : sorts, filter);
  }

  private static List<String> parseFields(String fields) {
    if (!StringUtils.hasText(fields)) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String part : fields.split(",")) {
      String token = part.trim();
      if (!token.isEmpty()) {
        out.add(token);
      }
    }
    return out;
  }

  private static List<SortSpec> parseSort(String sort) {
    if (!StringUtils.hasText(sort)) {
      return List.of();
    }
    List<SortSpec> out = new ArrayList<>();
    for (String part : sort.split(",")) {
      String token = part.trim();
      if (token.isEmpty()) {
        continue;
      }
      boolean desc = token.startsWith("-");
      if (desc || token.startsWith("+")) {
        token = token.substring(1).trim();
      }
      if (!token.isEmpty()) {
        out.add(new SortSpec(token, desc));
      }
    }
    return out;
  }

  private static FilterSpec parseFilter(String q) {
    if (!StringUtils.hasText(q)) {
      return null;
    }
    String token = q.trim();
    int sep = token.indexOf(':');
    if (sep <= 0 || sep >= token.length() - 1) {
      throw new RuntimeRecordException(
          "META_RUNTIME_INVALID_QUERY", "q 参数格式应为 field:value，例如 order_no:O-1");
    }
    return new FilterSpec(token.substring(0, sep).trim(), token.substring(sep + 1).trim());
  }

  public record SortSpec(String field, boolean descending) {}

  public record FilterSpec(String field, String value) {}
}
