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
   * 解析多条件过滤：{@code field:value}（等值）或 {@code field:op:value}（运算符），条件间 AND。
   *
   * <p><b>为什么分隔符是 {@code ;} 而不是 {@code ,}</b>：字段值是用户输入的自由文本，逗号是其中常见字符 （地址、备注、枚举列表）；用逗号分隔会把 {@code
   * note:hello,world} 误切成两个条件且第二个直接解析失败。 分号在业务值中罕见，且单条件（无分隔符）与旧契约完全兼容——存量 {@code field:value}
   * 请求零变更。
   *
   * <p>空段（如 {@code a:1;;b:2}）跳过而非报错：前端条件行删除后会留下空段，报错会把「删了一行筛选」 变成「列表加载失败」，对用户是不可解释的失败。
   *
   * <p><b>运算符语法（P1-3）</b>：{@code field:op:value}，op ∈ {@link FilterOp}。识别规则——第一冒号后若以「已知运算符 +
   * 冒号」开头则按运算符解析， 否则整段按等值（存量语义）。该规则保证含冒号的值零歧义：{@code url:http://x} 的 {@code http} 不是运算符 ⇒ 等值 {@code
   * http://x}；{@code note:a:b} 的 {@code a} 不是运算符 ⇒ 等值 {@code a:b}。 只有 {@code amount:gte:100}
   * 这类「第二段恰为运算符」的形状才走运算符路径。
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
            "q 参数每个条件格式应为 field:value 或 field:op:value（op ∈ "
                + FilterOp.tokens()
                + "），多条件用 ; 分隔，例如 status:APPROVED;amount:gte:99.99;title:contains:差旅");
      }
      String field = token.substring(0, sep).trim();
      String rest = token.substring(sep + 1).trim();
      FilterOp op = null;
      String value = rest;
      int opSep = rest.indexOf(':');
      if (opSep > 0) {
        FilterOp candidate = FilterOp.fromToken(rest.substring(0, opSep).trim());
        if (candidate != null) {
          op = candidate;
          value = rest.substring(opSep + 1).trim();
        }
      }
      if (op != null && value.isEmpty()) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_INVALID_QUERY, "q 条件运算符 " + op.token() + " 后缺少值: " + token);
      }
      out.add(new FilterSpec(field, op != null ? op : FilterOp.EQ, value));
    }
    return out;
  }

  public record SortSpec(String field, boolean descending) {}

  public record FilterSpec(String field, FilterOp op, String value) {
    /** 等值紧凑构造：存量调用方（仅 field:value 形状）零改动用。 */
    public FilterSpec(String field, String value) {
      this(field, FilterOp.EQ, value);
    }
  }

  /**
   * q 过滤运算符（P1-3 扩展集）。语义与 SQL 一一对应，前端条件构建器按 token 拼 q 串。
   *
   * <p>为什么不再支持 {@code like}/{@code startsWith}：{@code contains} 覆盖「包含」主场景， 前缀/后缀匹配可用 {@code
   * contains} + 排序近似；每加一个运算符就是一条 SQL 分支 + 一组测试 + 一个前端选项， 收敛在七个高价值运算符（等值/不等/四比较/包含/属于）。
   */
  public enum FilterOp {
    EQ("eq"),
    NE("ne"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    CONTAINS("contains"),
    IN("in");

    private final String token;

    FilterOp(String token) {
      this.token = token;
    }

    public String token() {
      return token;
    }

    /** 未知 token 返 null（调用方回退等值，保证含冒号的值零歧义）。 */
    public static FilterOp fromToken(String token) {
      for (FilterOp op : values()) {
        if (op.token.equals(token)) {
          return op;
        }
      }
      return null;
    }

    /** 全部 token（错误消息拼装用，顺序稳定）。 */
    public static String tokens() {
      StringBuilder sb = new StringBuilder();
      for (FilterOp op : values()) {
        if (sb.length() > 0) {
          sb.append('/');
        }
        sb.append(op.token);
      }
      return sb.toString();
    }
  }
}
