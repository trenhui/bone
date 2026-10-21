package com.bone.metadata.runtime.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 界面协议 {@code linkageRules} / {@code submitRules} 的<b>服务端执行期强制</b>（协议 §linkageRule.code /
 * §submitRule.code 的后半截承诺）。
 *
 * <p><b>为什么必须有</b>：协议把规则 {@code code} 定义为「前后端对账用：前端先拦、后端强制同一 code，日志与审计对齐，防两边文案漂移」。此前后端只对 {@code
 * submitRules} 做<b>引用完整性</b>校验（UI-REF-06/09：函数名合法、字段存在），<b>从不执行</b>规则本身。后果是规则沦为纯前端装饰： 绕过界面直接调
 * {@code POST /runtime/entities/{code}/records/{id}/actions/{action}} 即可提交「明细合计≠申报金额」「结束日期早于开始日期」的
 * 单据，且没有任何服务端痕迹。
 *
 * <p><b>与前端 {@code uiSchemaEngine.ts} 的同源纪律</b>：本类的比较符语义、路径穿透、容差、变体裁决全部<b>逐条对齐</b>前端实现（前端先拦、后端兜底，
 * 两边结论必须一致，否则用户会遇到「界面放行、接口拒绝」）。任何一侧改语义，另一侧必须同步——这是协议设计 {@code code} 的用途。
 *
 * <p><b>fail-closed 纪律</b>：规则配置错误（未知函数、{@code sum} 系缺 {@code compare}）一律产 ERROR
 * 阻断，不静默放行——配置错是建模侧问题， 放行等于把错误带到生产数据里。
 *
 * <p>纯函数式求值器：<b>不</b>依赖 Spring 上下文、无 IO；数据由调用方（动作编排）读好后传入，便于单测直接构造。
 */
@Service
@Slf4j
public class UiSchemaRuleEngine {

  /** 金额比较容差（与前端 {@code MONEY_TOLERANCE} 同值；DECIMAL(20,6) 精度口径，防浮点误伤）。 */
  public static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.005");

  /** 条件嵌套深度上限（协议 UI-REF-05）；超限 fail-closed 为不命中。 */
  public static final int MAX_COND_DEPTH = 3;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 单条规则违规。 */
  public record Violation(String code, String level, String message) {

    public static Violation error(String code, String message) {
      return new Violation(code, "ERROR", message);
    }
  }

  /** 求值上下文：{@code $me/$myteam/$all} 与静态必填（来自 {@code meta_field.is_required}）。 */
  public record Ctx(String me, List<String> myTeam, String all, Map<String, Boolean> baseRequired) {

    public static Ctx of(String me, Map<String, Boolean> baseRequired) {
      return new Ctx(me, List.of(), null, baseRequired == null ? Map.of() : baseRequired);
    }
  }

  // ============================== 对外入口 ==============================

  /**
   * 执行协议校验：{@code submitRules} 聚合断言 + {@code linkageRules} 必填短路。
   *
   * @param data 主记录数据（按 fieldCode 键，值为标量）
   * @param childRows 子表行：{@code childEntityCode}（或控件 fieldCode）→ 各行 Map。协议里 {@code items.amount}
   *     这类路径的 {@code items} 段即取自此。
   * @param ctx 求值上下文（{@code $me} 与静态必填）
   * @param enforceableFields 服务端可求值域（物理表真有列的字段码）。<b>空集 = 不施加 linkage 必填</b>（调用方拿不到已发布
   *     实体时的保守形态：宁可不强制，也不误伤）；{@code null} = 不设限（仅单测用，生产路径请传真实集合）。
   * @return 违规清单（空 = 通过）；WARNING 不阻断，由调用方决定是否提示
   */
  public List<Violation> enforce(
      JsonNode schema,
      Map<String, Object> data,
      Map<String, List<Map<String, Object>>> childRows,
      Ctx ctx,
      Set<String> enforceableFields) {
    List<Violation> out = new ArrayList<>();
    if (schema == null || !schema.isObject()) {
      return out;
    }
    // 视图合成：把 childRows 挂进 data 的 fieldCode 键下，使 items.amount 路径可被穿透（前端由表单结构天然具备）。
    Map<String, Object> merged = new LinkedHashMap<>(data == null ? Map.of() : data);
    if (childRows != null) {
      childRows.forEach((k, rows) -> merged.put(k, rows == null ? List.of() : rows));
    }
    runSubmits(schema.path("submitRules"), merged, ctx, out);
    runRequiredShortcuts(schema.path("linkageRules"), merged, ctx, out, enforceableFields);
    return out;
  }

  /** 四参重载：不设强制域（纯求值场景与单测）。 */
  public List<Violation> enforce(
      JsonNode schema,
      Map<String, Object> data,
      Map<String, List<Map<String, Object>>> childRows,
      Ctx ctx) {
    return enforce(schema, data, childRows, ctx, null);
  }

  /**
   * 服务端<b>字段锁</b>（数据完整性语义，与 UI {@code readonly} 渲染提示严格区分）。
   *
   * <p>返回在当前数据态下<b>不可变</b>的字段码集合：遍历 {@code fieldLockRules}，{@code when} 命中（或无 {@code when}）则把其
   * {@code fields} 纳入锁定集。与 {@link #enforce} 的关系：{@code enforce} 管「必填 / 聚合断言」（可缺失、可不完整），
   * 本方法管「已设值字段的不可变性」（冻结 / 对账锁），二者正交——锁的是「不可变」，不是「必填」。
   *
   * <p>纯函数，无 IO；{@code when} 求值复用 {@link #evalCond}（与前端同源纪律）。
   */
  public List<String> lockedFields(JsonNode schema, Map<String, Object> data, Ctx ctx) {
    List<String> locked = new ArrayList<>();
    JsonNode rules = schema == null ? null : schema.path("fieldLockRules");
    if (rules == null || !rules.isArray()) {
      return locked;
    }
    for (JsonNode r : rules) {
      if (r.path("enabled").isBoolean() && !r.path("enabled").asBoolean()) {
        continue;
      }
      if (r.has("when") && !evalCond(r.path("when"), data, ctx, 0)) {
        continue;
      }
      locked.addAll(textList(r.path("fields")));
    }
    return locked;
  }

  // ============================== submitRules ==============================

  private void runSubmits(JsonNode rules, Map<String, Object> data, Ctx ctx, List<Violation> out) {
    if (rules == null || !rules.isArray()) {
      return;
    }
    for (JsonNode r : rules) {
      // 规则级启停（协议 v1.2）：显式 false 时整条跳过，与前端 uiSchemaEngine 同语义。
      // 必须判 isBoolean && !asBoolean —— 缺字段不能当启用，也不能把非布尔值当 true。
      if (r.path("enabled").isBoolean() && !r.path("enabled").asBoolean()) {
        log.debug("[ui-rules] 规则已停用，跳过: {}", text(r, "code"));
        continue;
      }
      String code = text(r, "code");
      String name = text(r, "name");
      String tag = (name == null || name.isBlank()) ? (code == null ? "校验" : code) : name;
      String level = "WARNING".equals(text(r, "verifyType")) ? "WARNING" : "ERROR";

      // 生效前置 when：不命中整条跳过（如拒赔完整性只在 REJECTING 跑）。
      if (r.has("when") && !evalCond(r.path("when"), data, ctx, 0)) {
        continue;
      }
      List<String> fields = textList(r.path("fields"));
      String fn = text(r, "function");
      BigDecimal tolerance =
          r.path("params").path("tolerance").isNumber()
              ? r.path("params").path("tolerance").decimalValue()
              : MONEY_TOLERANCE;

      if ("unique".equals(fn)) {
        if (hasDupCombo(data, fields)) {
          out.add(new Violation(code, level, "【" + tag + "】" + text(r, "message")));
        }
        continue;
      }

      Object left;
      Object right = null;
      boolean rightDerived = false;
      switch (fn == null ? "" : fn) {
        case "compare" -> left = fields.isEmpty() ? null : pathVal(data, fields.get(0));
        case "sum" -> left = sumOf(data, fields);
        case "min" -> {
          List<Double> vs = nums(data, fields);
          left =
              vs.isEmpty() ? null : vs.stream().mapToDouble(Double::doubleValue).min().orElse(0d);
        }
        case "max" -> {
          List<Double> vs = nums(data, fields);
          left =
              vs.isEmpty() ? null : vs.stream().mapToDouble(Double::doubleValue).max().orElse(0d);
        }
        case "count" -> left = (double) countNonEmpty(data, fields);
        case "balance" -> {
          if (fields.size() < 2) {
            out.add(Violation.error(code, "配置错误：balance 需两组字段（借/贷），rule=" + tag));
            continue;
          }
          left = sumOf(data, List.of(fields.get(0)));
          right = sumOf(data, List.of(fields.get(1)));
          rightDerived = true;
        }
        case "consistency" -> {
          if (fields.isEmpty()) {
            out.add(Violation.error(code, "配置错误：consistency 需至少一个字段，rule=" + tag));
            continue;
          }
          left = scalarOrSum(data, fields.get(0));
          right = sumOf(data, fields.subList(1, fields.size()));
          rightDerived = true;
        }
        case "completeness" -> {
          left = (double) countNonEmpty(data, fields);
          JsonNode min = r.path("params").path("minReasons");
          right = (double) (min.isNumber() ? min.asInt() : fields.size());
          rightDerived = true;
        }
        default -> {
          out.add(Violation.error(code, "配置错误：未知函数 " + fn + "（rule=" + tag + "）"));
          continue;
        }
      }

      // sum/count/max/min/compare 的右值来自 compare；缺 compare = 配置错误（fail-closed）。
      if (!rightDerived) {
        JsonNode cmp = r.path("compare");
        if (!cmp.isObject() || cmp.isNull()) {
          out.add(Violation.error(code, "配置错误：" + fn + " 缺 compare 右值（rule=" + tag + "）"));
          continue;
        }
        if ("field".equals(text(cmp, "valueType"))) {
          String fc = text(cmp, "fieldCode");
          right = (fc == null || fc.isBlank()) ? null : pathVal(data, fc);
        } else {
          right = toJava(cmp.path("value"));
        }
      }
      String op = text(r, "operator");
      if (!passWithTolerance(op, left, right, tolerance)) {
        out.add(new Violation(code, level, "【" + tag + "】" + text(r, "message")));
      }
    }
  }

  // ============================== linkageRules（必填短路） ==============================

  /**
   * linkage 的<b>必填</b>维度在服务端强制。
   *
   * <p>只强制 {@code required}，不强制 {@code visible/readonly/disabled}：那三项是<b>渲染态</b>（界面表现），服务端无从也不应判定
   * 「字段隐藏了就不该有值」；而 {@code required} 是<b>数据完整性契约</b>（专票必须填税号），绕过界面提交缺税号单据是真实的数据缺陷。
   *
   * <p><b>强制域收窄</b>：{@code enforceableFields} 之外的字段（{@code ATTACHMENT} / {@code CHILD_TABLE}
   * 等主记录无列者）跳过—— 它们在主记录里恒为 {@code null}，强制等于把规则变成永久死锁（理赔样例的「材料缺失请补件」会拦掉所有申请）。
   */
  private void runRequiredShortcuts(
      JsonNode linkage,
      Map<String, Object> data,
      Ctx ctx,
      List<Violation> out,
      Set<String> enforceableFields) {
    Map<String, Boolean> required = new LinkedHashMap<>();
    if (linkage != null && linkage.isArray()) {
      for (JsonNode rule : linkage) {
        // 规则级启停（协议 v1.2）：与 submitRules 同语义，停用则不产生必填强制
        if (rule.path("enabled").isBoolean() && !rule.path("enabled").asBoolean()) {
          continue;
        }
        if (!evalCond(rule.path("when"), data, ctx, 0)) {
          continue;
        }
        JsonNode then = rule.path("then");
        if (!then.isArray()) {
          continue;
        }
        for (JsonNode t : then) {
          if (Boolean.TRUE.equals(t.path("required").asBoolean(false))) {
            String field = text(t, "fieldCode");
            if (field == null || field.isBlank()) {
              continue;
            }
            if (enforceableFields != null && !enforceableFields.contains(field)) {
              log.debug("[ui-rules] 跳过无物理列字段的必填强制: {}", field);
              continue;
            }
            required.put(field, Boolean.TRUE);
          }
        }
      }
    }
    required.forEach(
        (field, need) -> {
          if (!Boolean.TRUE.equals(need)) {
            return;
          }
          if (isEmptyValue(data.get(field))) {
            out.add(Violation.error(null, "【必填】" + field + " 不能为空"));
          }
        });
  }

  // ============================== 条件与比较（与前端逐条对齐） ==============================

  /** 条件求值：{@code all/any/not} 三选一与原子互斥；深度超限 fail-closed 为 false。 */
  boolean evalCond(JsonNode c, Map<String, Object> data, Ctx ctx, int depth) {
    if (c == null || c.isNull() || c.isMissingNode()) {
      return true;
    }
    if (depth > MAX_COND_DEPTH) {
      log.warn("[ui-rules] 条件嵌套深度 > {}，fail-closed 为不命中", MAX_COND_DEPTH);
      return false;
    }
    if (c.has("all")) {
      for (JsonNode x : c.path("all")) {
        if (!evalCond(x, data, ctx, depth + 1)) {
          return false;
        }
      }
      return true;
    }
    if (c.has("any")) {
      for (JsonNode x : c.path("any")) {
        if (evalCond(x, data, ctx, depth + 1)) {
          return true;
        }
      }
      return false;
    }
    if (c.has("not")) {
      return !evalCond(c.path("not"), data, ctx, depth + 1);
    }
    String fieldCode = text(c, "fieldCode");
    String op = text(c, "operator");
    if (fieldCode == null || op == null) {
      return true;
    }
    Object left = pathVal(data, fieldCode);
    Object right;
    if ("field".equals(text(c, "valueType"))) {
      String cf = text(c, "compareField");
      right = (cf == null || cf.isBlank()) ? null : pathVal(data, cf);
    } else {
      right = resolveCtxVar(c.path("value"), ctx);
    }
    if ("$all".equals(right)) {
      return true;
    }
    return cmp(op, left, right);
  }

  /**
   * 条件右值：先解上下文变量，<b>再按 JSON 原生类型取值</b>。
   *
   * <p><b>为什么不能用 {@code asText()}</b>：{@code {"operator":"eq","value":true}} 的值是布尔，若被转成字符串 {@code
   * "true"}， 与布尔字段比较恒为 false——「布尔条件永不命中」且无任何报错（与前端 {@code true === true} 行为分叉，正是本方法要防的漂移）。
   */
  private static Object resolveCtxVar(JsonNode value, Ctx ctx) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return null;
    }
    if (value.isTextual()) {
      Object v = resolveCtxVar(value.asText(), ctx);
      // 命中上下文变量则替换；否则按文本原样（不是变量）。
      if (v != value.asText() || isDollarVar(value.asText())) {
        return v;
      }
      return value.asText();
    }
    return toJava(value);
  }

  private static boolean isDollarVar(String v) {
    return "$me".equals(v) || "$myteam".equals(v) || "$all".equals(v);
  }

  private static Object resolveCtxVar(String v, Ctx ctx) {
    if (ctx == null) {
      return v;
    }
    return switch (v == null ? "" : v) {
      case "$me" -> ctx.me();
      case "$myteam" -> ctx.myTeam();
      case "$all" -> ctx.all() == null ? "$all" : ctx.all();
      default -> v;
    };
  }

  private static boolean cmp(String op, Object left, Object right) {
    if (op == null) {
      return false;
    }
    return switch (op) {
      case "isNull" -> isEmptyValue(left);
      case "isNotNull" -> !isEmptyValue(left);
      case "eq" -> eqWithTolerance(left, right, MONEY_TOLERANCE);
      case "neq" -> !eqWithTolerance(left, right, MONEY_TOLERANCE);
      case "gt", "gte", "lt", "lte" -> cmpOrd(op, left, right);
      case "in" -> right instanceof List<?> l && l.contains(left);
      case "notIn" -> right instanceof List<?> l && !l.contains(left);
      case "between" -> right instanceof List<?> l
          && l.size() >= 2
          && asDouble(left) >= asDouble(l.get(0))
          && asDouble(left) <= asDouble(l.get(1));
        // ISO 日期（yyyy-MM-dd / yyyy-MM-dd HH:mm:ss）字典序即时间序，前端同口径。
      case "earlierThan" -> left != null && right != null && str(left).compareTo(str(right)) < 0;
      case "laterThan" -> left != null && right != null && str(left).compareTo(str(right)) > 0;
      default -> false;
    };
  }

  /** 有序比较：双数值走数值，否则字符串（与前端 cmpOrd 同源）。 */
  private static boolean cmpOrd(String op, Object left, Object right) {
    Double ln = asDoubleOrNull(left);
    Double rn = asDoubleOrNull(right);
    int cmp;
    if (ln != null && rn != null) {
      cmp = Double.compare(ln, rn);
    } else {
      cmp = str(left).compareTo(str(right));
    }
    return switch (op) {
      case "gt" -> cmp > 0;
      case "gte" -> cmp >= 0;
      case "lt" -> cmp < 0;
      case "lte" -> cmp <= 0;
      default -> false;
    };
  }

  /**
   * 带容差的通过判定（与前端 {@code passWithTolerance} 同源）。
   *
   * <p>容差内算<b>通过</b>（含边界：差额 == 容差即过），与「差额 &gt; 0.01 不得保存」这类业务口径一致；前端同款，避免两边在 0.01 边界给出相反结论。
   */
  private static boolean passWithTolerance(
      String op, Object left, Object right, BigDecimal tolerance) {
    Double ln = asDoubleOrNull(left);
    Double rn = asDoubleOrNull(right);
    if (ln != null && rn != null && ("eq".equals(op) || "neq".equals(op))) {
      BigDecimal diff = BigDecimal.valueOf(ln).subtract(BigDecimal.valueOf(rn)).abs();
      boolean ok = diff.compareTo(tolerance) <= 0;
      return "eq".equals(op) ? ok : !ok;
    }
    return cmp(op, left, right);
  }

  private static boolean eqWithTolerance(Object left, Object right, BigDecimal tolerance) {
    Double ln = asDoubleOrNull(left);
    Double rn = asDoubleOrNull(right);
    if (ln != null && rn != null) {
      return BigDecimal.valueOf(ln).subtract(BigDecimal.valueOf(rn)).abs().compareTo(tolerance)
          <= 0;
    }
    return left == null ? right == null : left.equals(right);
  }

  // ============================== 路径与聚合 ==============================

  /**
   * 穿透数组路径求值：{@code items.amount} → 各子表行的 {@code amount} 组成的 List（多值）/ 标量（单值）/ {@code null}（无命中）。
   *
   * <p>与前端 {@code pathVal} 同语义：前端无命中返回 {@code undefined}，此处返回 {@code null}（Java 侧等价）。
   */
  @SuppressWarnings("unchecked")
  static Object pathVal(Map<String, Object> data, String path) {
    if (path == null || path.isBlank()) {
      return null;
    }
    Object cur = data;
    for (String seg : path.split("\\.")) {
      if (cur instanceof List<?> list) {
        List<Object> next = new ArrayList<>();
        for (Object o : list) {
          if (o instanceof Map<?, ?> m && m.containsKey(seg)) {
            next.add(m.get(seg));
          }
        }
        cur = next;
      } else if (cur instanceof Map<?, ?> m && m.containsKey(seg)) {
        cur = m.get(seg);
      } else {
        return null;
      }
    }
    return cur;
  }

  /** 数值聚合（sum/min/max）：只收可转数值的值，非数值忽略（与前端 numVals 同源）。 */
  private static List<Double> nums(Map<String, Object> data, List<String> fields) {
    List<Double> out = new ArrayList<>();
    for (String f : fields) {
      Object v = pathVal(data, f);
      if (v instanceof List<?> list) {
        for (Object it : list) {
          Double d = asDoubleOrNull(it);
          if (d != null) {
            out.add(d);
          }
        }
      } else {
        Double d = asDoubleOrNull(v);
        if (d != null) {
          out.add(d);
        }
      }
    }
    return out;
  }

  private static double sumOf(Map<String, Object> data, List<String> fields) {
    return nums(data, fields).stream().mapToDouble(Double::doubleValue).sum();
  }

  /** 非空计数（count/completeness）：数组计非空元素，标量非空计 1。 */
  private static int countNonEmpty(Map<String, Object> data, List<String> fields) {
    int n = 0;
    for (String f : fields) {
      Object v = pathVal(data, f);
      if (v instanceof List<?> list) {
        for (Object it : list) {
          if (!isEmptyValue(it)) {
            n++;
          }
        }
      } else if (!isEmptyValue(v)) {
        n++;
      }
    }
    return n;
  }

  /** consistency 左值：标量原样返回；数组则求和（发票金额 vs 清单列合计）。 */
  private static Object scalarOrSum(Map<String, Object> data, String field) {
    Object v = pathVal(data, field);
    if (v instanceof List<?> list) {
      double s = 0;
      for (Object it : list) {
        Double d = asDoubleOrNull(it);
        if (d != null) {
          s += d;
        }
      }
      return s;
    }
    return v;
  }

  /**
   * 本单判重（与前端 {@code hasDupCombo} 同源：数组列逐行取组合键；标量组合全非空即命中）。
   *
   * <p><b>口径说明</b>：仅做<b>本单内</b>判重。跨单查重（如发票号全局唯一）需要查库，不在本纯函数职责内——协议 {@code params.scope} 即为此预留，
   * 由唯一索引 / 服务端查询承担。
   */
  private static boolean hasDupCombo(Map<String, Object> data, List<String> fields) {
    List<Object> cols = new ArrayList<>();
    boolean anyArray = false;
    for (String f : fields) {
      Object c = pathVal(data, f);
      cols.add(c);
      if (c instanceof List<?>) {
        anyArray = true;
      }
    }
    if (!anyArray) {
      // 标量组合：全非空即提醒（缺值不扰）
      return cols.stream().allMatch(c -> !isEmptyValue(c));
    }
    int len = 0;
    for (Object c : cols) {
      if (c instanceof List<?> l) {
        len = Math.max(len, l.size());
      }
    }
    Set<String> seen = new LinkedHashSet<>();
    for (int i = 0; i < len; i++) {
      StringBuilder key = new StringBuilder();
      for (Object c : cols) {
        Object cell = (c instanceof List<?> l) ? (i < l.size() ? l.get(i) : null) : c;
        key.append(str(cell)).append('');
      }
      if (!seen.add(key.toString())) {
        return true;
      }
    }
    return false;
  }

  // ============================== 工具 ==============================

  private static boolean isEmptyValue(Object v) {
    return v == null || v.equals("") || (v instanceof List<?> l && l.isEmpty());
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }

  private static Double asDoubleOrNull(Object o) {
    if (o == null || o.equals("")) {
      return null;
    }
    if (o instanceof Number n) {
      return n.doubleValue();
    }
    if (o instanceof Boolean) {
      return null;
    }
    try {
      return Double.valueOf(String.valueOf(o));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static double asDouble(Object o) {
    Double d = asDoubleOrNull(o);
    return d == null ? 0d : d;
  }

  private static String text(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode n = node.path(field);
    return (n.isMissingNode() || n.isNull()) ? null : n.asText();
  }

  private static List<String> textList(JsonNode arr) {
    List<String> out = new ArrayList<>();
    if (arr != null && arr.isArray()) {
      for (JsonNode n : arr) {
        if (n.isTextual()) {
          out.add(n.asText());
        }
      }
    }
    return out;
  }

  /** {@code compare.value} 的 JSON→Java 归一（数字用 BigDecimal，避免 double 精度在容差比较里放大）。 */
  private static Object toJava(JsonNode n) {
    if (n == null || n.isMissingNode() || n.isNull()) {
      return null;
    }
    if (n.isNumber()) {
      return n.decimalValue();
    }
    if (n.isBoolean()) {
      return n.asBoolean();
    }
    if (n.isArray()) {
      List<Object> out = new ArrayList<>();
      n.forEach(x -> out.add(toJava(x)));
      return out;
    }
    return n.asText();
  }

  /** 解析 schema 文本（容错：非法 JSON 返回 null → 上游视为「无协议规则」，不阻断业务）。 */
  public static JsonNode parse(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      log.warn("[ui-rules] schema JSON 解析失败，跳过协议规则强制: {}", e.getMessage());
      return null;
    }
  }
}
