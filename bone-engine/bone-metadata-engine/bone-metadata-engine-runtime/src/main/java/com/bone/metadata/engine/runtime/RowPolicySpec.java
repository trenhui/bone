package com.bone.metadata.engine.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 行级 ACL 策略声明（Runtime §4.7.1 · 对标 Supabase RLS「策略即 WHERE」）。
 *
 * <p>声明形态（meta_entity.row_policies JSON，可空）：
 *
 * <pre>{@code
 * {
 *   "owner_field": "created_by",   // 默认归属字段；未声明取 created_by（行级四件套）
 *   "rules": [
 *     { "name": "owner_or_approver", "effect": "allow",
 *       "when": "created_by = :operator OR approver_id = :operator" }
 *   ]
 * }
 * }</pre>
 *
 * <p>安全约束：{@code when} 仅支持 {@code <列> = :operator} 原子比较 + AND/OR 组合；列名必须是实体 已声明列且物理表存在该列（{@code
 * columnChecker} 校验），否则该规则被忽略（保守退回已校验规则）， 杜绝任意 SQL 注入。变量仅绑定 {@code :operator}（当前操作者，参数化）。
 *
 * <p><b>AND/OR 语义保真</b>：{@code buildWhere} 按 {@code when} 的表达式结构**递归**生成 SQL（AND 优先、
 * 括号嵌套原样保留），不做「拆原子后一律 OR 拼接」的拍平。后者是历史实现且是**权限放大**缺陷—— 建模者写 {@code a = :operator AND b =
 * :operator}（意图：两个条件同时成立）却生成 {@code a OR b}， 可见行集比声明意图**更宽**；更糟的是含括号时拍平会**整段丢弃**括号内除首个以外的比较 （实测
 * {@code (a OR b) AND c} 生成 {@code a OR a OR c}，{@code b} 凭空消失）。权限类判定的 默认方向必须是
 * fail-closed，宁可拒绝也不能放大，故此处严格保真。
 */
public record RowPolicySpec(String ownerField, List<Rule> rules) {

  private static final Logger log = LoggerFactory.getLogger(RowPolicySpec.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 变量白名单：当前仅支持操作者（数值用户 ID 或管理员 null 之外的值）。 */
  private static final String OPERATOR_VAR = ":operator";

  /** 单条策略规则：effect 恒为 allow（v0.48 范围），when 为受约束的 WHERE 片段。 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Rule(String name, String effect, String when) {}

  /** 无策略声明（null / 空 JSON）→ Optional.empty；解析失败 → 返回空策略（保守：仅 owner_field 生效）。 */
  public static RowPolicySpec parse(String json, Predicate<String> columnChecker) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      RawSpec raw = MAPPER.readValue(json, RawSpec.class);
      List<Rule> valid = new ArrayList<>();
      List<Rule> declared = raw.rules() == null ? List.<Rule>of() : raw.rules();
      for (Rule rule : declared) {
        if (rule == null || rule.when() == null || rule.when().isBlank()) {
          continue;
        }
        if (!"allow".equalsIgnoreCase(rule.effect())) {
          log.warn("行策略仅支持 allow 效果，忽略 rule={} effect={}", rule.name(), rule.effect());
          continue;
        }
        if (!isSafeWhen(rule.when(), columnChecker)) {
          log.warn("行策略 when 含未知列或非法语法，忽略 rule={} when={}", rule.name(), rule.when());
          continue;
        }
        valid.add(rule);
      }
      String ownerField =
          raw.ownerField() == null || raw.ownerField().isBlank() ? "created_by" : raw.ownerField();
      return new RowPolicySpec(ownerField, valid);
    } catch (JsonProcessingException ex) {
      log.warn("row_policies JSON 解析失败，按无策略处理（owner_field 默认 created_by）: {}", ex.getMessage());
      return new RowPolicySpec("created_by", List.of());
    }
  }

  /** 白名单校验：仅允许「列 = :operator」原子比较 + AND/OR/括号组合，列名必须通过 columnChecker。 */
  private static boolean isSafeWhen(String when, Predicate<String> columnChecker) {
    String expr = when.trim();
    // 拆分顶层 AND/OR（忽略括号包裹）；空括号不匹配则整体作为单个原子比较
    List<String> atoms = splitAtoms(expr);
    if (atoms.isEmpty()) {
      atoms = List.of(expr);
    }
    for (String atom : atoms) {
      String a = atom.trim();
      if (a.startsWith("(") && a.endsWith(")")) {
        a = a.substring(1, a.length() - 1).trim();
        if (a.isBlank()) {
          return false;
        }
        // 递归校验括号内子表达式
        if (!isSafeWhen(a, columnChecker)) {
          return false;
        }
        continue;
      }
      if (!isAtomicComparison(a, columnChecker)) {
        return false;
      }
    }
    return true;
  }

  /** 按顶层 AND/OR 拆分原子片段（忽略括号内的连接词）；AND/OR 必须满足单词边界（防 `:operator` 的 "or" 误判）。 */
  private static List<String> splitAtoms(String expr) {
    List<String> atoms = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < expr.length(); i++) {
      char c = expr.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (depth == 0) {
        if (expr.regionMatches(true, i, "AND", 0, 3) && isWordDelimited(expr, i, 3)) {
          atoms.add(expr.substring(start, i).trim());
          start = i + 3;
          i += 2;
        } else if (expr.regionMatches(true, i, "OR", 0, 2) && isWordDelimited(expr, i, 2)) {
          atoms.add(expr.substring(start, i).trim());
          start = i + 2;
          i += 1;
        }
      }
    }
    if (start < expr.length()) {
      atoms.add(expr.substring(start).trim());
    }
    return atoms;
  }

  /** AND/OR 关键字边界：前一个字符非字母数字非冒号，后一个字符非字母数字。 */
  private static boolean isWordDelimited(CharSequence expr, int i, int len) {
    char prev = i == 0 ? ' ' : expr.charAt(i - 1);
    char next = i + len >= expr.length() ? ' ' : expr.charAt(i + len);
    return !Character.isLetterOrDigit(prev) && prev != ':' && !Character.isLetterOrDigit(next);
  }

  /** 原子比较：{@code <列> = :operator}（列名白名单 + 无其他符号）。 */
  private static boolean isAtomicComparison(String atom, Predicate<String> columnChecker) {
    String a = atom.trim();
    int eq = a.indexOf(" = ");
    if (eq <= 0) {
      return false;
    }
    String col = a.substring(0, eq).trim();
    String rhs = a.substring(eq + 3).trim();
    if (!OPERATOR_VAR.equals(rhs)) {
      return false;
    }
    if (!col.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      return false;
    }
    return columnChecker.test(col);
  }

  /**
   * 生成参数化 WHERE 片段（不含 AND 前缀）；无规则时退化为 {@code `owner_field` = :rowPolicy_0}。
   *
   * <p><b>AND/OR 保真</b>：按 {@code when} 的表达式结构递归渲染（AND 优先级高于 OR，括号原样保留）。 历史实现把 {@code when} 拆成原子后一律用
   * {@code OR} 拼接，等价于「AND 声明 ⇒ 实际放开」—— 对权限策略而言是**放大**缺陷，且含括号时会整段丢弃非首个比较。
   *
   * <p>变量与参数绑定：{@code :operator} 一律改写为 {@code :rowPolicy_{N}}（N 为渲染序号）， 由 {@link #bindParams}
   * 按同一渲染过程绑定，占位符与参数个数**结构上不可能错位**。
   *
   * @param table 物理表名（保留参数以维持调用方签名；渲染不需要它，列名已由 parse 期白名单保证）
   * @param tableHasOwnerColumn 表是否具备 owner 物理列；false 时不注入 owner 条件
   * @param operatorId 当前操作者 ID（保留参数；实际绑定由 bindParams 完成）
   */
  public String buildWhere(String table, boolean tableHasOwnerColumn, String operatorId) {
    return renderWhere(tableHasOwnerColumn).sql();
  }

  /** 渲染产物：SQL 片段 + 该片段引用的占位符个数（供 bindParams 对齐）。 */
  private record Rendered(String sql, int placeholders) {}

  /**
   * 唯一渲染入口：SQL 与占位符计数由同一次递归产出，避免二者各算一遍而错位 （历史实现的 bindParams 用 {@code splitAtoms().size()+1}
   * 估算，在括号嵌套下与实际占位符不符）。
   */
  private Rendered renderWhere(boolean tableHasOwnerColumn) {
    StringBuilder sql = new StringBuilder("(");
    Counter ctr = new Counter();
    boolean first = true;
    if (tableHasOwnerColumn) {
      sql.append('`').append(ownerField).append("` = ").append(placeholder(ctr.next()));
      first = false;
    }
    // 多条规则之间是 OR（任一规则命中即可见）
    for (Rule rule : rules) {
      if (rule == null || rule.when() == null || rule.when().isBlank()) {
        continue;
      }
      if (!first) {
        sql.append(" OR ");
      }
      sql.append(renderExpr(rule.when(), ctr));
      first = false;
    }
    if (first) {
      // 既无 owner 列又无有效规则：生成恒假条件而非空括号（`()` 是 SQL 语法错误）
      sql.append("1 = 0");
    }
    sql.append(")");
    return new Rendered(sql.toString(), ctr.count());
  }

  /** 简单可变计数器（渲染过程内共享，用于给每个原子比较分配唯一占位符序号）。 */
  private static final class Counter {
    private int n;

    int next() {
      return n++;
    }

    int count() {
      return n;
    }
  }

  private static String placeholder(int idx) {
    return ":rowPolicy_" + idx;
  }

  /**
   * 递归渲染布尔表达式（文法：{@code or := and (OR and)*}，{@code and := atom (AND atom)*}， {@code atom := '(' or
   * ')' | comparison}）。
   *
   * <p>AND 优先级高于 OR 与 SQL 一致；括号内递归渲染并加括号，从而**完整保留建模者声明的结构**。
   */
  private String renderExpr(String expr, Counter ctr) {
    List<String> orParts = new ArrayList<>();
    for (String andPart : splitTop(expr, "OR")) {
      orParts.add(renderAnd(andPart, ctr));
    }
    return orParts.size() == 1 ? orParts.get(0) : "(" + String.join(" OR ", orParts) + ")";
  }

  /** 渲染一个 AND 分支：单个原子直接输出，多个原子用 AND 连接并加括号。 */
  private String renderAnd(String expr, Counter ctr) {
    List<String> atoms = new ArrayList<>();
    for (String atom : splitTop(expr, "AND")) {
      String rendered = renderAtom(atom, ctr);
      if (rendered != null) {
        atoms.add(rendered);
      }
    }
    if (atoms.isEmpty()) {
      return null;
    }
    return atoms.size() == 1 ? atoms.get(0) : "(" + String.join(" AND ", atoms) + ")";
  }

  /** 渲染原子：括号组递归，非括号组取「列 = :operator」。不是合法原子则返回 null（跳过）。 */
  private String renderAtom(String rawAtom, Counter ctr) {
    String a = rawAtom.trim();
    if (a.isEmpty()) {
      return null;
    }
    if (a.startsWith("(") && a.endsWith(")")) {
      String inner = renderExpr(a.substring(1, a.length() - 1).trim(), ctr);
      // 内层若渲染不出内容（如空括号），跳过该原子而非生成 `()`
      return inner == null || inner.isEmpty() ? null : inner;
    }
    int eq = a.indexOf(" = ");
    if (eq <= 0) {
      return null;
    }
    String col = a.substring(0, eq).trim();
    return '`' + col + "` = " + placeholder(ctr.next());
  }

  /** 按顶层关键字切分（忽略括号内部）。AND/OR 需满足单词边界——防 {@code :operator} 里 "or" 子串被误判为连接词。 */
  private static List<String> splitTop(String expr, String keyword) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    int klen = keyword.length();
    for (int i = 0; i < expr.length(); i++) {
      char c = expr.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (depth == 0
          && expr.regionMatches(true, i, keyword, 0, klen)
          && isWordDelimited(expr, i, klen)) {
        parts.add(expr.substring(start, i).trim());
        start = i + klen;
        i += klen - 1;
      }
    }
    parts.add(expr.substring(start).trim());
    return parts;
  }

  /** 绑定 buildWhere 产生的全部 :rowPolicy_N 参数（统一为 operatorId 字符串比较）。 */
  public void bindParams(
      org.springframework.jdbc.core.namedparam.MapSqlParameterSource params, String operatorId) {
    // 与 buildWhere 同一渲染过程取占位符个数：owner 恒占 1（tableHasOwnerColumn=true 时），
    // 余下按规则原子数。owner 占位符序号 0 与规则占位符序号不冲突。
    int count = renderWhere(true).placeholders();
    for (int i = 0; i < count; i++) {
      params.addValue("rowPolicy_" + i, operatorId);
    }
  }

  /** 是否有任何可生效规则（无规则 = 仅 owner_field）。 */
  public boolean hasRules() {
    return !rules.isEmpty();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RawSpec(
      @com.fasterxml.jackson.annotation.JsonProperty("owner_field") String ownerField,
      List<Rule> rules) {}
}
