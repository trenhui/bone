package com.bone.metadata.engine.runtime;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/** 模式 B 列表查询参数（fields / sort / q）。 */
public record RuntimePageQuery(
    List<String> selectFields, List<SortSpec> sortSpecs, List<FilterSpec> filters) {

  public static final RuntimePageQuery EMPTY = new RuntimePageQuery(null, null, null);

  public static RuntimePageQuery parse(String fields, String sort, String q) {
    List<String> select = parseFields(fields);
    List<SortSpec> sorts = parseSort(sort);
    List<FilterSpec> filters = parseFilters(q);
    if (select.isEmpty() && sorts.isEmpty() && filters.isEmpty()) {
      return EMPTY;
    }
    return new RuntimePageQuery(
        select.isEmpty() ? null : select,
        sorts.isEmpty() ? null : sorts,
        filters.isEmpty() ? null : filters);
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

  /**
   * 解析多条件等值过滤：{@code field:value;field2:value2}，条件间 AND。
   *
   * <p><b>为什么分隔符是 {@code ;} 而不是 {@code ,}</b>：字段值是用户输入的自由文本，逗号是其中常见字符 （地址、备注、枚举列表）；用逗号分隔会把 {@code
   * note:hello,world} 误切成两个条件且第二个直接解析失败。 分号在业务值中罕见，且单条件（无分隔符）与旧契约完全兼容——存量 {@code field:value}
   * 请求零变更。
   *
   * <p>空段（如 {@code a:1;;b:2}）跳过而非报错：前端条件行删除后会留下空段，报错会把「删了一行筛选」 变成「列表加载失败」，对用户是不可解释的失败。
   */
  private static List<FilterSpec> parseFilters(String q) {
    if (!StringUtils.hasText(q)) {
      return List.of();
    }
    List<FilterSpec> out = new ArrayList<>();
    for (String part : q.split(";")) {
      String token = part.trim();
      if (token.isEmpty()) {
        continue;
      }
      int sep = token.indexOf(':');
      if (sep <= 0 || sep >= token.length() - 1) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_INVALID_QUERY,
            "q 参数每个条件格式应为 field:value，多条件用 ; 分隔，例如 status:APPROVED;amount:99.99");
      }
      out.add(new FilterSpec(token.substring(0, sep).trim(), token.substring(sep + 1).trim()));
    }
    return out;
  }

  public record SortSpec(String field, boolean descending) {}

  public record FilterSpec(String field, String value) {}
}
