package com.bone.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 结构化条件求值器（引擎自带实现，纯函数、零依赖、安全）。
 *
 * <p><b>语义与平台 UI 协议规则逐条对齐</b>（同算子、同金额容差、同子表路径穿透、同 {@code $me} 解析），以满足 「前端先拦、后端强制同一结论」的同源纪律；但<b>不引入
 * SpEL</b>——求值面只接受结构化 JSON 条件，不具备执行任意 代码的能力（流程条件是业务人员可编辑的元数据，表达式求值器一旦能调方法/构造对象即等于开放 RCE）。
 *
 * <p><b>fail-closed 纪律</b>：
 *
 * <ul>
 *   <li>条件嵌套超深 → 判<b>不成立</b>（而不是抛错或放行）
 *   <li>原子条件缺 {@code fieldCode}/{@code operator} → 判<b>不成立</b>
 *   <li>未知算子 → 判<b>不成立</b>，并由 {@link WorkflowBranchEvaluator} 的静态检查在发布期拦下
 * </ul>
 *
 * <p>注意：这里的「不成立」与 {@link WorkflowBranchEvaluator} 的「报错」是两层不同的防线——求值器只回答条件真假， 是否允许「都不成立」由分支选择器按
 * fail-closed 规则裁决。
 */
public final class StructuredConditionEvaluator implements WorkflowConditionEvaluator {

  /** 金额比较容差（与平台 UI 协议规则的 {@code MONEY_TOLERANCE} 同值）。 */
  public static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.005");

  /** 条件嵌套深度上限（与平台 UI 协议规则的 {@code MAX_COND_DEPTH} 同值）。 */
  public static final int MAX_COND_DEPTH = 3;

  /**
   * 条件 DSL 的算子全集（DSL 契约，非某一实现的可选项）。
   *
   * <p>它同时是<b>发布期静态检查的白名单</b>与<b>运行期求值的支持集</b>——两处共用同一个常量，避免「求值器支持了 某算子、静态检查却把它判为未知」这种双真源漂移。
   */
  public static final java.util.Set<String> SUPPORTED_OPERATORS =
      java.util.Set.of(
          "isNull",
          "isNotNull",
          "eq",
          "neq",
          "gt",
          "gte",
          "lt",
          "lte",
          "in",
          "notIn",
          "between",
          "earlierThan",
          "laterThan");

  @Override
  public boolean evaluate(JsonNode condition, Map<String, Object> data, ConditionContext ctx) {
    return evalCond(condition, data == null ? Map.of() : data, ctx, 0);
  }

  /**
   * 条件求值：{@code all/any/not} 三选一与原子互斥。
   *
   * @param condition 条件节点
   * @param data 业务数据
   * @param ctx 主体上下文
   * @param depth 当前嵌套深度
   * @return 条件是否成立
   */
  public boolean evalCond(
      JsonNode condition, Map<String, Object> data, ConditionContext ctx, int depth) {
    if (condition == null || condition.isNull() || condition.isMissingNode()) {
      return true;
    }
    if (depth > MAX_COND_DEPTH) {
      return false;
    }
    boolean hasAll = condition.has("all");
    boolean hasAny = condition.has("any");
    boolean hasNot = condition.has("not");
    if (hasAll) {
      for (JsonNode child : condition.path("all")) {
        if (!evalCond(child, data, ctx, depth + 1)) {
          return false;
        }
      }
      return true;
    }
    if (hasAny) {
      for (JsonNode child : condition.path("any")) {
        if (evalCond(child, data, ctx, depth + 1)) {
          return true;
        }
      }
      return false;
    }
    if (hasNot) {
      return !evalCond(condition.path("not"), data, ctx, depth + 1);
    }
    String fieldCode = text(condition, "fieldCode");
    String operator = text(condition, "operator");
    if (fieldCode == null || fieldCode.isBlank() || operator == null || operator.isBlank()) {
      return false;
    }
    Object left = pathVal(data, fieldCode);
    Object right;
    if ("field".equals(text(condition, "valueType"))) {
      String compareField = text(condition, "compareField");
      right = (compareField == null || compareField.isBlank()) ? null : pathVal(data, compareField);
    } else {
      right = resolveValue(condition.path("value"), ctx);
    }
    if ("$all".equals(right)) {
      return true;
    }
    return compare(operator, left, right);
  }

  /** 比较符语义（与平台 UI 协议规则同源）。 */
  private static boolean compare(String operator, Object left, Object right) {
    if (operator == null) {
      return false;
    }
    return switch (operator) {
      case "isNull" -> isEmpty(left);
      case "isNotNull" -> !isEmpty(left);
      case "eq" -> eqWithTolerance(left, right);
      case "neq" -> !eqWithTolerance(left, right);
      case "gt", "gte", "lt", "lte" -> compareOrdered(operator, left, right);
      case "in" -> right instanceof List<?> list && list.contains(left);
      case "notIn" -> right instanceof List<?> list && !list.contains(left);
      case "between" -> right instanceof List<?> list
          && list.size() >= 2
          && asDouble(left) >= asDouble(list.get(0))
          && asDouble(left) <= asDouble(list.get(1));
      case "earlierThan" -> left != null && right != null && str(left).compareTo(str(right)) < 0;
      case "laterThan" -> left != null && right != null && str(left).compareTo(str(right)) > 0;
      default -> false;
    };
  }

  /** 有序比较：双数值走数值，否则走字符串。 */
  private static boolean compareOrdered(String operator, Object left, Object right) {
    Double leftNumber = asDoubleOrNull(left);
    Double rightNumber = asDoubleOrNull(right);
    int result;
    if (leftNumber != null && rightNumber != null) {
      result = Double.compare(leftNumber, rightNumber);
    } else {
      result = str(left).compareTo(str(right));
    }
    return switch (operator) {
      case "gt" -> result > 0;
      case "gte" -> result >= 0;
      case "lt" -> result < 0;
      case "lte" -> result <= 0;
      default -> false;
    };
  }

  /** 等值：双数值走容差（防 DECIMAL 与 double 的精度误伤）。 */
  private static boolean eqWithTolerance(Object left, Object right) {
    Double leftNumber = asDoubleOrNull(left);
    Double rightNumber = asDoubleOrNull(right);
    if (leftNumber != null && rightNumber != null) {
      BigDecimal diff =
          BigDecimal.valueOf(leftNumber).subtract(BigDecimal.valueOf(rightNumber)).abs();
      return diff.compareTo(MONEY_TOLERANCE) <= 0;
    }
    return left == null ? right == null : left.equals(right);
  }

  /** 路径求值：{@code items.amount} → 各子表行的 {@code amount}（多值 / 标量 / null）。 */
  static Object pathVal(Map<String, Object> data, String path) {
    if (path == null || path.isBlank()) {
      return null;
    }
    Object current = data;
    for (String segment : path.split("\\.")) {
      if (current instanceof List<?> list) {
        List<Object> next = new ArrayList<>();
        for (Object item : list) {
          if (item instanceof Map<?, ?> map && map.containsKey(segment)) {
            next.add(map.get(segment));
          }
        }
        current = next;
      } else if (current instanceof Map<?, ?> map && map.containsKey(segment)) {
        current = map.get(segment);
      } else {
        return null;
      }
    }
    return current;
  }

  /** 条件右值：先解主体变量，再按 JSON 原生类型取值（布尔不可退化为字符串）。 */
  private static Object resolveValue(JsonNode value, ConditionContext ctx) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return null;
    }
    if (value.isTextual()) {
      String text = value.asText();
      if ("$me".equals(text)) {
        return ctx == null ? null : ctx.me();
      }
      if ("$myteam".equals(text)) {
        return ctx == null ? null : ctx.myTeam();
      }
      if ("$all".equals(text)) {
        return ctx == null ? "$all" : (ctx.all() == null ? "$all" : ctx.all());
      }
      return text;
    }
    return toJava(value);
  }

  private static Object toJava(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    if (node.isNumber()) {
      return node.decimalValue();
    }
    if (node.isBoolean()) {
      return node.asBoolean();
    }
    if (node.isArray()) {
      List<Object> out = new ArrayList<>();
      node.forEach(item -> out.add(toJava(item)));
      return out;
    }
    return node.asText();
  }

  private static boolean isEmpty(Object value) {
    return value == null || value.equals("") || (value instanceof List<?> list && list.isEmpty());
  }

  private static String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static Double asDoubleOrNull(Object value) {
    if (value == null || value.equals("")) {
      return null;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof Boolean) {
      return null;
    }
    try {
      return Double.valueOf(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static double asDouble(Object value) {
    Double number = asDoubleOrNull(value);
    return number == null ? 0d : number;
  }

  private static String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.path(field);
    return (value.isMissingNode() || value.isNull()) ? null : value.asText();
  }
}
