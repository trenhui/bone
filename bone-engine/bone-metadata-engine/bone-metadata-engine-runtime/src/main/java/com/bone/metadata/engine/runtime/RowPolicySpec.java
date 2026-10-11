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
        System.out.println(
            "RPS_DBG rule="
                + rule.name()
                + " effect="
                + rule.effect()
                + " when=["
                + rule.when()
                + "]");
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
   * 生成参数化 WHERE 片段（不含 AND 前缀），形如 {@code (`created_by` = :rowPolicy_0 OR `approver_id` =
   * :rowPolicy_1)}；无规则时退化为 {@code `owner_field` = :rowPolicy_0}。
   *
   * <p>变量与参数绑定：{@code :operator} 统一绑定为 {@code operatorId}（数值用户 ID 字符串）。
   */
  public String buildWhere(String table, boolean tableHasOwnerColumn, String operatorId) {
    StringBuilder sb = new StringBuilder("(");
    boolean first = true;
    if (tableHasOwnerColumn) {
      sb.append("`").append(ownerField).append("` = :rowPolicy_0");
      first = false;
    }
    int idx = tableHasOwnerColumn ? 1 : 0;
    for (Rule rule : rules) {
      for (String atom : splitAtoms(rule.when())) {
        String a = atom.trim();
        if (a.startsWith("(") && a.endsWith(")")) {
          a = a.substring(1, a.length() - 1).trim();
        }
        int eq = a.indexOf(" = ");
        if (eq <= 0) {
          continue;
        }
        String col = a.substring(0, eq).trim();
        if (!first) {
          sb.append(" OR ");
        }
        sb.append("`").append(col).append("` = :rowPolicy_").append(idx);
        first = false;
        idx++;
      }
    }
    sb.append(")");
    return sb.toString();
  }

  /** 绑定 buildWhere 产生的全部 :rowPolicy_N 参数（统一为 operatorId 字符串比较）。 */
  public void bindParams(
      org.springframework.jdbc.core.namedparam.MapSqlParameterSource params, String operatorId) {
    int count =
        (rules.stream().mapToInt(r -> splitAtoms(r.when()).size()).sum()) + 1; // owner_field 恒占 1
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
