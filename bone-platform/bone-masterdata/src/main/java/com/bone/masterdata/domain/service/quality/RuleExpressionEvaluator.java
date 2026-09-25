package com.bone.masterdata.domain.service.quality;

import com.bone.masterdata.domain.model.quality.DataQualityRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 数据质量规则表达式求值器（纯领域服务，零框架依赖）。
 *
 * <p><b>表达式语法</b>：{@code k=v} 键值以 {@code ;} 分隔，键名大小写不敏感。示例：
 *
 * <pre>{@code
 * field=code                                  // NOT_NULL / UNIQUE
 * field=code;pattern=^\d{6}$                  // FORMAT
 * field=amount;min=0;max=1000000              // RANGE
 * field=dept_code;entity=123;targetField=code // REFERENCE
 * }</pre>
 *
 * <p><b>支持的规则类型</b>：{@code NOT_NULL}、{@code UNIQUE}、{@code FORMAT}、{@code RANGE}、{@code
 * REFERENCE}。其余类型（如 {@code CUSTOM} 脚本）不做猜测式求值，由 {@link #evaluate} 返回 {@link
 * RuleEvaluation#unsupported} 并在检查报告中显式列出——延续「宁可声明没算，也不产出近似结论」的约定。
 *
 * <p><b>空值约定</b>：除 {@code NOT_NULL} 外，空白字段值一律跳过（不算违规），避免同一处「未填写」被多条规则重复计数。
 */
public final class RuleExpressionEvaluator {

  /** 引用规则的目标：某实体的某字段取值集合。 */
  public record ReferenceTarget(Long entityId, String field) {}

  private static final String KEY_FIELD = "field";
  private static final String KEY_PATTERN = "pattern";
  private static final String KEY_MIN = "min";
  private static final String KEY_MAX = "max";
  private static final String KEY_ENTITY = "entity";
  private static final String KEY_TARGET_FIELD = "targetField";

  private static final String TYPE_NOT_NULL = "NOT_NULL";
  private static final String TYPE_UNIQUE = "UNIQUE";
  private static final String TYPE_FORMAT = "FORMAT";
  private static final String TYPE_RANGE = "RANGE";
  private static final String TYPE_REFERENCE = "REFERENCE";

  private static final Set<String> SUPPORTED_TYPES =
      Set.of(TYPE_NOT_NULL, TYPE_UNIQUE, TYPE_FORMAT, TYPE_RANGE, TYPE_REFERENCE);

  /** 受支持的类型清单，供前端/OpenAPI 提示使用。 */
  public static Set<String> supportedTypes() {
    return SUPPORTED_TYPES;
  }

  /** 类型是否受支持（大小写不敏感）。 */
  public boolean supports(String type) {
    return SUPPORTED_TYPES.contains(normalize(type));
  }

  /**
   * 校验表达式能否被求值（创建/更新规则时前置校验，避免非法配置入库后才在检查时暴露）。
   *
   * <p>不受支持的类型一律放行（返回 {@code null}）：允许先录入、检查时显式标注未求值。
   *
   * @return 错误说明；{@code null} 表示合法
   */
  public String validate(String type, String expression) {
    String normalized = normalize(type);
    if (!SUPPORTED_TYPES.contains(normalized)) {
      return null;
    }
    final Expr expr;
    try {
      expr = Expr.parse(expression);
    } catch (IllegalArgumentException ex) {
      return "规则表达式无法解析: " + ex.getMessage();
    }
    if (expr.field() == null) {
      return "规则表达式缺少 field=<字段编码>";
    }
    switch (normalized) {
      case TYPE_FORMAT:
        return expr.param(KEY_PATTERN) == null ? "FORMAT 规则需提供 pattern=<正则表达式>" : null;
      case TYPE_RANGE:
        if (expr.param(KEY_MIN) == null && expr.param(KEY_MAX) == null) {
          return "RANGE 规则需至少提供 min 或 max";
        }
        return decimalOrNull(expr.param(KEY_MIN)) == null
                && decimalOrNull(expr.param(KEY_MAX)) == null
            ? "RANGE 规则的 min/max 必须是数值"
            : null;
      case TYPE_REFERENCE:
        return expr.param(KEY_ENTITY) == null ? "REFERENCE 规则需提供 entity=<目标实体ID>" : null;
      default:
        return null;
    }
  }

  /** REFERENCE 规则的引用目标（供应用层预加载目标实体取值）；非 REFERENCE 或表达式不可解析时返回空。 */
  public Optional<ReferenceTarget> referenceTargetOf(DataQualityRule rule) {
    if (rule == null || !TYPE_REFERENCE.equals(normalize(rule.getType()))) {
      return Optional.empty();
    }
    final Expr expr;
    try {
      expr = Expr.parse(rule.getExpression());
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
    if (expr.field() == null || expr.param(KEY_ENTITY) == null) {
      return Optional.empty();
    }
    String targetField = expr.param(KEY_TARGET_FIELD);
    return parseLong(expr.param(KEY_ENTITY))
        .map(
            entityId ->
                new ReferenceTarget(entityId, targetField == null ? expr.field() : targetField));
  }

  /**
   * 对一批记录求值。
   *
   * @param references REFERENCE 规则的取值集合，key 为 {@code entityId#field}（由 {@link #referenceTargetOf}
   *     的解析结果预加载）
   */
  public RuleEvaluation evaluate(
      DataQualityRule rule, List<RecordFields> records, Map<String, Set<String>> references) {
    String type = normalize(rule.getType());
    String name = rule.getName() == null ? null : rule.getName().value();
    if (!SUPPORTED_TYPES.contains(type)) {
      return RuleEvaluation.unsupported(
          rule.getId(),
          name,
          rule.getType(),
          "暂不支持的规则类型，支持的取值: " + String.join("/", SUPPORTED_TYPES));
    }
    final Expr expr;
    try {
      expr = Expr.parse(rule.getExpression());
    } catch (IllegalArgumentException ex) {
      return RuleEvaluation.unsupported(rule.getId(), name, type, "规则表达式无法解析: " + ex.getMessage());
    }
    if (expr.field() == null) {
      return RuleEvaluation.unsupported(rule.getId(), name, type, "规则表达式缺少 field=<字段编码>");
    }
    String field = expr.field();
    List<RuleEvaluation.Violation> violations = new ArrayList<>();
    switch (type) {
      case TYPE_NOT_NULL -> {
        for (RecordFields record : records) {
          if (record.value(field) == null) {
            violations.add(
                new RuleEvaluation.Violation(record.recordId(), field, "字段 " + field + " 不能为空"));
          }
        }
      }
      case TYPE_UNIQUE -> {
        Map<String, Long> counts = new HashMap<>();
        for (RecordFields record : records) {
          String value = record.value(field);
          if (value != null) {
            counts.merge(value, 1L, Long::sum);
          }
        }
        for (RecordFields record : records) {
          String value = record.value(field);
          if (value != null && counts.get(value) > 1) {
            violations.add(
                new RuleEvaluation.Violation(
                    record.recordId(), field, "字段 " + field + " 取值重复: " + value));
          }
        }
      }
      case TYPE_FORMAT -> {
        String patternText = expr.param(KEY_PATTERN);
        if (patternText == null) {
          return RuleEvaluation.unsupported(
              rule.getId(), name, type, "FORMAT 规则缺少 pattern=<正则表达式>");
        }
        final Pattern pattern;
        try {
          pattern = Pattern.compile(patternText);
        } catch (PatternSyntaxException ex) {
          return RuleEvaluation.unsupported(
              rule.getId(), name, type, "pattern 不是合法正则: " + patternText);
        }
        for (RecordFields record : records) {
          String value = record.value(field);
          if (value != null && !pattern.matcher(value).matches()) {
            violations.add(
                new RuleEvaluation.Violation(
                    record.recordId(), field, "字段 " + field + " 取值不符合格式: " + value));
          }
        }
      }
      case TYPE_RANGE -> {
        BigDecimal min = decimalOrNull(expr.param(KEY_MIN));
        BigDecimal max = decimalOrNull(expr.param(KEY_MAX));
        if (min == null && max == null) {
          return RuleEvaluation.unsupported(rule.getId(), name, type, "RANGE 规则缺少 min/max");
        }
        for (RecordFields record : records) {
          String raw = record.value(field);
          if (raw == null) {
            continue;
          }
          BigDecimal value = decimalOrNull(raw);
          if (value == null) {
            violations.add(
                new RuleEvaluation.Violation(
                    record.recordId(), field, "字段 " + field + " 不是数值: " + raw));
          } else if ((min != null && value.compareTo(min) < 0)
              || (max != null && value.compareTo(max) > 0)) {
            violations.add(
                new RuleEvaluation.Violation(
                    record.recordId(), field, "字段 " + field + " 取值超出范围: " + raw));
          }
        }
      }
      case TYPE_REFERENCE -> {
        Optional<ReferenceTarget> target = referenceTargetOf(rule);
        if (target.isEmpty()) {
          return RuleEvaluation.unsupported(
              rule.getId(), name, type, "REFERENCE 规则缺少 entity=<目标实体ID>");
        }
        ReferenceTarget ref = target.get();
        Set<String> allowed =
            references == null ? null : references.get(ref.entityId() + "#" + ref.field());
        if (allowed == null) {
          return RuleEvaluation.unsupported(
              rule.getId(),
              name,
              type,
              "引用目标实体取值未加载: entity=" + ref.entityId() + ", field=" + ref.field());
        }
        for (RecordFields record : records) {
          String value = record.value(field);
          if (value != null && !allowed.contains(value)) {
            violations.add(
                new RuleEvaluation.Violation(
                    record.recordId(), field, "字段 " + field + " 取值不在引用范围内: " + value));
          }
        }
      }
    }
    return RuleEvaluation.of(rule.getId(), name, type, violations);
  }

  private static String normalize(String type) {
    return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
  }

  private static BigDecimal decimalOrNull(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(text.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Optional<Long> parseLong(String text) {
    try {
      return Optional.of(Long.valueOf(text.trim()));
    } catch (NumberFormatException | NullPointerException ex) {
      return Optional.empty();
    }
  }

  /** {@code k=v;k=v} 形式的表达式解析结果。 */
  private record Expr(Map<String, String> params) {

    static Expr parse(String expression) {
      if (expression == null || expression.isBlank()) {
        throw new IllegalArgumentException("表达式为空");
      }
      Map<String, String> params = new LinkedHashMap<>();
      for (String segment : expression.split("[;&\\n]")) {
        String piece = segment.trim();
        if (piece.isEmpty()) {
          continue;
        }
        int eq = piece.indexOf('=');
        if (eq <= 0) {
          throw new IllegalArgumentException("片段不是 k=v 形式: " + piece);
        }
        params.put(
            piece.substring(0, eq).trim().toLowerCase(Locale.ROOT), piece.substring(eq + 1).trim());
      }
      if (params.isEmpty()) {
        throw new IllegalArgumentException("表达式为空");
      }
      return new Expr(params);
    }

    String field() {
      return params.get(KEY_FIELD);
    }

    /** 键名大小写不敏感（解析时已统一小写，故这里同步小写后再取）。 */
    String param(String key) {
      String value = params.get(key == null ? "" : key.toLowerCase(Locale.ROOT));
      return value == null || value.isEmpty() ? null : value;
    }
  }
}
