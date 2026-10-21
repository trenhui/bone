package com.bone.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 凭证生成公式/条件纯函数求值器（业财一体方案 doc/design/modules/12 §4.5，D15）。
 *
 * <p>「凭证模板 × 科目映射」双层里的<b>行结构求值</b>内核：模板 {@code line_rules} 的 {@code amount_formula} 与 {@code
 * condition} 由本引擎求值——无 IO、无 Spring 依赖、可单测（黄金样例即其回归集，方案 §11）。
 *
 * <p><b>金额公式语法子集</b>（doc 12 §4.5 固化）：
 *
 * <ul>
 *   <li>{@code sum(items.amount)}——子表（CHILD_TABLE fieldCode）聚合求和；
 *   <li>{@code items.amount}——当前子行的字段值（按行展开 scope=CHILD 时）；
 *   <li>{@code header.amount} / {@code amount}——头表字段（裸字段名按头表解析）；
 *   <li>四则运算 {@code + - * /} 与括号，乘除优先；除法保留 6 位小数（对齐 DECIMAL(20,6) 存储口径）。
 * </ul>
 *
 * <p><b>条件语法</b>（{@code {child?, field, op, value}}，复用 UI 协议 linkageRules when 方言的子集）： {@code
 * eq/ne/gt/gte/lt/lte/in/earlierThan/laterThan}；{@code child} 非空时按当前子行求值，否则按头表； 字段值为 {@code null}
 * 时条件不命中（条件行的设计语义就是「仅当适用才生成该行」）。
 *
 * <p><b>fail-closed 边界</b>：金额公式遇未知路径/字段/语法错误抛 {@link VoucherFormulaException}—— 配错模板必须显式失败，绝不静默生成 0
 * 金额或丢行（方案 R13）。摘要渲染例外：未知占位符原样保留 （摘要是展示文案，不阻断过账）。
 *
 * <p>value 只支持字面量；{@code valueType=field} 的字段间比较由调用方预先解析为字面量后再传入（引擎保持无状态）。
 */
public final class VoucherFormulaEngine {

  /** 除法保留小数位（与业财一体金额存储 DECIMAL(20,6) 对齐：6 位小数，非 6 位有效数字）。 */
  private static final int DIVISION_SCALE = 6;

  private VoucherFormulaEngine() {}

  /** 公式求值上下文：头表行 + 子表行集（按 CHILD_TABLE fieldCode 键入）+ 当前子行。 */
  public record Ctx(
      Map<String, Object> header,
      Map<String, List<Map<String, Object>>> childRows,
      Map<String, Object> currentChildRow) {

    public static Ctx ofHeader(Map<String, Object> header) {
      return new Ctx(header, Map.of(), null);
    }
  }

  /** 公式/条件求值失败（fail-closed）：未知路径、字段缺失、语法错误。 */
  public static class VoucherFormulaException extends RuntimeException {
    public VoucherFormulaException(String message) {
      super(message);
    }

    public VoucherFormulaException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  // ===================== 金额公式 =====================

  /** 求值金额公式（见类 javadoc 语法子集）。 */
  public static BigDecimal evalAmount(String formula, Ctx ctx) {
    if (formula == null || formula.isBlank()) {
      throw new VoucherFormulaException("金额公式为空");
    }
    try {
      return new Parser(formula.trim(), ctx).parseExpression();
    } catch (VoucherFormulaException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new VoucherFormulaException("金额公式求值失败: " + formula, ex);
    }
  }

  // ===================== 条件求值 =====================

  /** 求值条件（见类 javadoc 语法子集）；条件结构非法时 fail-closed 抛错，字段为 null 时不命中。 */
  @SuppressWarnings("unchecked")
  public static boolean evalCondition(Map<String, Object> condition, Ctx ctx) {
    Objects.requireNonNull(condition, "condition 不能为空");
    Object child = condition.get("child");
    Object field = condition.get("field");
    Object op = condition.get("op");
    if (field == null || op == null) {
      throw new VoucherFormulaException("条件缺少 field/op: " + condition);
    }
    Map<String, Object> row =
        (child != null && !child.toString().isBlank()) ? ctx.currentChildRow() : ctx.header();
    if (row == null) {
      return false; // 所需作用域不存在（如头行作用域却传了子行条件）→ 不命中
    }
    Object actual = row.get(field.toString());
    if (actual == null) {
      return false;
    }
    Object expected = condition.get("value");
    return switch (op.toString()) {
      case "eq" -> Objects.equals(actual.toString(), str(expected));
      case "ne" -> !Objects.equals(actual.toString(), str(expected));
      case "in" -> expected instanceof Collection<?> c
          && c.stream().anyMatch(v -> Objects.equals(actual.toString(), str(v)));
      case "gt", "gte", "lt", "lte" -> compareOrdered(actual, expected, op.toString(), condition);
      case "earlierThan", "laterThan" -> compareDates(actual, expected, op.toString(), condition);
      default -> throw new VoucherFormulaException("条件算子不支持: " + op + "（" + condition + "）");
    };
  }

  private static boolean compareOrdered(
      Object actual, Object expected, String op, Map<String, Object> condition) {
    int cmp;
    try {
      cmp = toDecimal(actual).compareTo(toDecimal(expected));
    } catch (RuntimeException ex) {
      throw new VoucherFormulaException("有序比较两侧须可转数值: " + condition, ex);
    }
    return switch (op) {
      case "gt" -> cmp > 0;
      case "gte" -> cmp >= 0;
      case "lt" -> cmp < 0;
      default -> cmp <= 0;
    };
  }

  /** 日期比较：ISO 形态（yyyy-MM-dd[ HH:mm:ss]）字典序即时间序；不可解析时 fail-closed。 */
  private static boolean compareDates(
      Object actual, Object expected, String op, Map<String, Object> condition) {
    String a = actual.toString();
    String e = str(expected);
    int cmp = a.compareTo(e);
    return "earlierThan".equals(op) ? cmp < 0 : cmp > 0;
  }

  // ===================== 摘要渲染 =====================

  /** 渲染摘要模板（{@code {applicant} {period} 差旅报销}）；未知占位符原样保留，不阻断。 */
  public static String renderSummary(String template, Map<String, Object> values) {
    if (template == null || template.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < template.length()) {
      char c = template.charAt(i);
      if (c == '{') {
        int end = template.indexOf('}', i);
        if (end > i) {
          String key = template.substring(i + 1, end);
          Object value = values == null ? null : values.get(key);
          out.append(value == null ? "{" + key + "}" : value.toString());
          i = end + 1;
          continue;
        }
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  // ===================== 内部：递归下降解析 =====================

  private static final class Parser {
    private final String src;
    private final Ctx ctx;
    private int pos;

    private Parser(String src, Ctx ctx) {
      this.src = src;
      this.ctx = ctx;
    }

    /** expression := term (('+' | '-') term)* */
    private BigDecimal parseExpression() {
      BigDecimal value = parseTerm();
      while (true) {
        skipWs();
        if (match('+')) {
          value = value.add(parseTerm());
        } else if (match('-')) {
          value = value.subtract(parseTerm());
        } else {
          return value;
        }
      }
    }

    /** term := factor (('*' | '/') factor)* */
    private BigDecimal parseTerm() {
      BigDecimal value = parseFactor();
      while (true) {
        skipWs();
        if (match('*')) {
          value = value.multiply(parseFactor());
        } else if (match('/')) {
          value = value.divide(parseFactor(), DIVISION_SCALE, RoundingMode.HALF_UP);
        } else {
          return value;
        }
      }
    }

    /** factor := number | path | 'sum(' path ')' | '(' expression ')' */
    private BigDecimal parseFactor() {
      skipWs();
      if (match('(')) {
        BigDecimal inner = parseExpression();
        expect(')');
        return inner;
      }
      if (src.startsWith("sum(", pos)) {
        pos += 4;
        int close = src.indexOf(')', pos);
        if (close < 0) {
          throw new VoucherFormulaException("sum( 缺少右括号: " + src);
        }
        String path = src.substring(pos, close).trim();
        pos = close + 1;
        return sumOf(path);
      }
      if (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
        return readNumber();
      }
      return resolvePath(readIdentifier());
    }

    private BigDecimal readNumber() {
      int start = pos;
      while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
        pos++;
      }
      String raw = src.substring(start, pos);
      try {
        return new BigDecimal(raw);
      } catch (NumberFormatException ex) {
        throw new VoucherFormulaException("非法数值: " + raw);
      }
    }

    private String readIdentifier() {
      skipWs();
      int start = pos;
      while (pos < src.length()
          && (Character.isLetterOrDigit(src.charAt(pos))
              || src.charAt(pos) == '_'
              || src.charAt(pos) == '.')) {
        pos++;
      }
      if (start == pos) {
        throw new VoucherFormulaException(
            "公式此处无法解析: " + src.substring(Math.min(pos, src.length())));
      }
      return src.substring(start, pos);
    }

    private void skipWs() {
      while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
        pos++;
      }
    }

    private boolean match(char c) {
      if (pos < src.length() && src.charAt(pos) == c) {
        pos++;
        return true;
      }
      return false;
    }

    private void expect(char c) {
      if (!match(c)) {
        throw new VoucherFormulaException("期望 '" + c + "' : " + src);
      }
    }

    /** 路径解析：header.x / 子表fieldCode.x / 裸字段名（按头表）。 */
    private BigDecimal resolvePath(String path) {
      Object value = resolveRaw(path);
      return toDecimal(value);
    }

    private Object resolveRaw(String path) {
      int dot = path.indexOf('.');
      if (dot < 0) {
        Object v = ctx.header().get(path);
        if (v == null) {
          throw new VoucherFormulaException("头表字段不存在: " + path);
        }
        return v;
      }
      String head = path.substring(0, dot);
      String field = path.substring(dot + 1);
      Map<String, Object> row;
      if ("header".equals(head)) {
        row = ctx.header();
      } else {
        row = ctx.currentChildRow();
        if (row == null) {
          throw new VoucherFormulaException("子行作用域缺失（路径 " + path + "）");
        }
      }
      Object v = row.get(field);
      if (v == null) {
        throw new VoucherFormulaException("字段不存在: " + path);
      }
      return v;
    }

    /** sum(子表fieldCode.字段)：子表行集聚合；空集合 = 0（与 ReimbursementChildAmountAggregator 的 COALESCE 口径一致）。 */
    private BigDecimal sumOf(String path) {
      int dot = path.indexOf('.');
      if (dot < 0) {
        throw new VoucherFormulaException("sum( 须为 子表.字段 形态: " + path);
      }
      String childFieldCode = path.substring(0, dot);
      String field = path.substring(dot + 1);
      List<Map<String, Object>> rows = ctx.childRows().get(childFieldCode);
      if (rows == null) {
        throw new VoucherFormulaException("子表不存在: " + childFieldCode);
      }
      BigDecimal total = BigDecimal.ZERO;
      for (Map<String, Object> row : rows) {
        Object v = row.get(field);
        if (v != null) {
          total = total.add(toDecimal(v));
        }
      }
      return total;
    }
  }

  private static BigDecimal toDecimal(Object value) {
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      return new BigDecimal(n.toString());
    }
    try {
      return new BigDecimal(value.toString());
    } catch (NumberFormatException | NullPointerException ex) {
      throw new VoucherFormulaException("值不可转数值: " + value);
    }
  }

  private static String str(Object value) {
    return value == null ? null : value.toString();
  }
}
