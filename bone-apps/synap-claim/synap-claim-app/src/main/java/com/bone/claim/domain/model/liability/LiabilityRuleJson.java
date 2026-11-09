package com.bone.claim.domain.model.liability;

import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * `t_adj_liability.liability_rule_json` 的领域值对象（M2 ② Shadow 阶段的「可映射子集」）。
 *
 * <p><b>它存在的唯一理由</b>：把「JSON 影子与标量谁是事实」这件事变成可判定的不变量。② 阶段标量仍是真源， JSON 是并写的影子；影子一旦写歪，③
 * 双读就会按歪掉的键算钱，且<b>不会报错</b>——金额引擎出错的典型表现是静默算错。 所以每次写入都要证明 {@code scalar == project(json)}
 * 逐位成立，证明不了就拒绝提交。
 *
 * <p><b>为什么 A 类键在 ② 是「单一数值」而不是对象</b>：一聚合列的目标形态允许分段 / 按金额区间 / 按累计， 但那三种表达在 ② <b>不可回滚</b>——{@code
 * DECIMAL(6,2)} 压不回"0–1000 报 80%、1000 以上报 90%"。故 ② 只解锁 「单比例 / 单笔绝对免赔 / 单额度」这 3 个可映射取值，超集表达由 {@link
 * #CURRENT_SCHEMA_VERSION} 之后的新版本承载 （见 §9.1.1 ④；解锁点写在这里，读代码的人不必回翻文档）。
 *
 * <p><b>键分类（唯一真源在 §9.1.1「键分类表」，此处只落到类型）</b>：
 *
 * <ul>
 *   <li>A 必需计算参数（{@link #AMOUNT_KEYS}）：3 个可映射键，缺一个就算不出钱 ⇒ 构造期即拒绝；
 *   <li>B 可选限定维度（{@link #DIMENSION_KEYS}）：6 个新键，缺键 = 不施加该约束（历史行本就不存在这些语义）；
 *   <li>未知键 / 缺 {@code schemaVersion} / 未知版本：解码期拒绝（在 {@code LiabilityRuleJsonCodec}），
 *       因为"这是不是一个合法文档"属于 Schema 层，不属于值对象。
 * </ul>
 *
 * @param schemaVersion 语义版本；不认识的值一律拒绝，绝不按旧语义解释未来数据
 * @param payPercent 赔付比例（百分数，如 90 表示 90%），与标量列 {@code pay_percent} 必须逐位相等
 * @param liabilityDeduct 年度免赔额，与标量列 {@code liability_deduct} 必须逐位相等
 * @param liabilityLimit 责任额度上限，与标量列 {@code liability_limit} 必须逐位相等
 * @param dimensions B 类维度（键必须是 {@link #DIMENSION_KEYS} 之一；值是该维度的**原始 JSON 对象文本**，
 *     原样保存、原样写回——不认识的子结构一旦被解析再序列化就会被改写，而"我们还不理解的数据"最不该被改写）
 */
public record LiabilityRuleJson(
    int schemaVersion,
    BigDecimal payPercent,
    BigDecimal liabilityDeduct,
    BigDecimal liabilityLimit,
    Map<String, String> dimensions) {

  /** 当前语义版本。改动 A 类键的**取值形态**（单值 → 分段 / 区间 / 累计）必须同时 bump 它。 */
  public static final int CURRENT_SCHEMA_VERSION = 1;

  public static final String KEY_SCHEMA_VERSION = "schemaVersion";
  public static final String KEY_PAY_PERCENT = "pay_percent";
  public static final String KEY_LIABILITY_DEDUCT = "liability_deduct";
  public static final String KEY_LIABILITY_LIMIT = "liability_limit";

  /** A 类：必需计算参数（缺一即算不出钱，构造期拒绝）。 */
  public static final List<String> AMOUNT_KEYS =
      List.of(KEY_PAY_PERCENT, KEY_LIABILITY_DEDUCT, KEY_LIABILITY_LIMIT);

  /** B 类：可选限定维度（缺键 = 不施加该约束，与历史行为一致）。 */
  public static final List<String> DIMENSION_KEYS =
      List.of(
          "quota_controller",
          "restrict_scope",
          "restrict_object",
          "restrict_out_insure",
          "times_limit",
          "adjustment_rule");

  /** 全部合法键（用于解码期 {@code additionalProperties: false} 的第一道判定）。 */
  public static final Set<String> KNOWN_KEYS;

  static {
    Set<String> keys = new java.util.LinkedHashSet<>();
    keys.add(KEY_SCHEMA_VERSION);
    keys.addAll(AMOUNT_KEYS);
    keys.addAll(DIMENSION_KEYS);
    KNOWN_KEYS = Collections.unmodifiableSet(keys);
  }

  public LiabilityRuleJson {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "liability_rule_json 的 schemaVersion=%d 无法识别（当前仅支持 %d）：" + "未来版本的数据不得按旧语义解释，须先升级读取侧",
          schemaVersion,
          CURRENT_SCHEMA_VERSION);
    }
    requireAmount(KEY_PAY_PERCENT, payPercent);
    requireAmount(KEY_LIABILITY_DEDUCT, liabilityDeduct);
    requireAmount(KEY_LIABILITY_LIMIT, liabilityLimit);
    dimensions = normalizeDimensions(dimensions);
  }

  /**
   * 由旧标量装配影子（② 的唯一来源）。
   *
   * <p>只接收 3 个可映射键：其余 6 个 B 类键没有旧列可回落，编造一个默认值等于凭空新增赔付条件。
   */
  public static LiabilityRuleJson ofScalars(
      BigDecimal payPercent, BigDecimal liabilityDeduct, BigDecimal liabilityLimit) {
    return new LiabilityRuleJson(
        CURRENT_SCHEMA_VERSION, payPercent, liabilityDeduct, liabilityLimit, Map.of());
  }

  /**
   * 只替换 3 个可映射键，**保留**已存在的 B 类维度。
   *
   * <p>编辑路径必须走这里而不是 {@link #ofScalars}：草稿行若已经带了 B 类维度（未来入口写入的）， 用 {@code ofScalars}
   * 重建会把它们整段丢掉——"改个比例顺手删掉限定条件"，页面上看不出，金额上立刻生效。
   */
  public LiabilityRuleJson withProjection(
      BigDecimal payPercent, BigDecimal liabilityDeduct, BigDecimal liabilityLimit) {
    return new LiabilityRuleJson(
        schemaVersion, payPercent, liabilityDeduct, liabilityLimit, dimensions);
  }

  /** 取某 A 类键的投影值（② 只允许这 3 个键参与投影）。 */
  public BigDecimal project(String key) {
    if (KEY_PAY_PERCENT.equals(key)) {
      return payPercent;
    }
    if (KEY_LIABILITY_DEDUCT.equals(key)) {
      return liabilityDeduct;
    }
    if (KEY_LIABILITY_LIMIT.equals(key)) {
      return liabilityLimit;
    }
    throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "键「%s」不是可映射的计算参数，没有标量投影", key);
  }

  /**
   * 断言 {@code scalar == project(json)}（② 的核心不变量）。
   *
   * <p>用 {@code compareTo} 而不是 {@code equals}：{@code 90.0} 与 {@code 90.00} 是同一个金额，
   * 精度不同不代表值变了；但**值不同必须炸**，不允许多一分或少一分。
   */
  public void assertProjectsTo(
      BigDecimal scalarPayPercent, BigDecimal scalarDeduct, BigDecimal scalarLimit) {
    assertSame(KEY_PAY_PERCENT, payPercent, scalarPayPercent);
    assertSame(KEY_LIABILITY_DEDUCT, liabilityDeduct, scalarDeduct);
    assertSame(KEY_LIABILITY_LIMIT, liabilityLimit, scalarLimit);
  }

  private static void assertSame(String key, BigDecimal shadow, BigDecimal scalar) {
    BigDecimal expected = scalar == null ? BigDecimal.ZERO : scalar;
    if (shadow.compareTo(expected) != 0) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "JSON 影子与标量不一致：%s 标量=%s、JSON=%s。" + "影子写歪会让 ③ 双读按错误值算钱且不报错，故拒绝提交",
          key,
          expected.toPlainString(),
          shadow.toPlainString());
    }
  }

  private static void requireAmount(String key, BigDecimal value) {
    if (value == null) {
      throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "缺少必需计算参数 %s：没有它就算不出赔付额，缺省按 0 会静默少赔", key);
    }
  }

  private static Map<String, String> normalizeDimensions(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, String> ordered = new TreeMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      String key = e.getKey();
      if (!DIMENSION_KEYS.contains(key)) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "「%s」不是已知的 B 类维度（合法值：%s）：拼错的键不得被静默忽略",
            key,
            String.join(" / ", DIMENSION_KEYS));
      }
      String value = e.getValue();
      if (value == null || value.isBlank()) {
        throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "维度 %s 的值为空：缺键才表示不施加约束", key);
      }
      ordered.put(key, value);
    }
    return Collections.unmodifiableMap(ordered);
  }
}
