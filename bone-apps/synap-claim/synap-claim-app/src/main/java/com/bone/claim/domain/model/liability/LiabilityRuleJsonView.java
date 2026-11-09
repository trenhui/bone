package com.bone.claim.domain.model.liability;

import com.bone.claim.domain.model.adjustment.engine.RuleGap;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * `liability_rule_json` 的<b>读侧</b>视图（M2 ③ 3a）：允许键缺失，按<b>单个键</b>回落。
 *
 * <p><b>为什么它与 {@link LiabilityRuleJson} 是两个类型</b>：两者回答的是相反的问题。 写侧（{@code
 * LiabilityRuleJson}）要证明"影子与标量逐位一致"，所以三个 A 类键<b>必须齐全</b>， 缺一个就拒绝写入；读侧要回答"这一版责任按哪套值算钱"，历史行整列为
 * NULL、未来行可能只覆盖部分键， 所以<b>允许稀疏</b>。把两种语义塞进一个类型，只能靠放松写侧不变量（等于放弃 ② 的保障）或 在读侧造一个"假
 * 0"（等于静默少赔）。分开是唯一不牺牲任何一边的做法。
 *
 * <p><b>本条最重要的判定口径：按键回落，禁止整列判断。</b> "聚合 JSON 非空 ⇒ 所有键都走 JSON"是这类迁移最典型的错误写法：只要某行只配了一个键， 另外两个键就会拿到
 * 0（静默少赔）或抛异常（整行读不出）。故 {@link #resolve(String, BigDecimal)} 逐键判断，绝不看"整列是否为空"。
 *
 * <p><b>两侧都空必须拒绝</b>：A 类键在 JSON 缺失、旧标量列又是 NULL 时，没有任何真源可回落， 此时按 0 计算会静默少赔，故 fail-closed 并报出具体键名。
 *
 * <p><b>B 类维度缺失 = 不施加约束</b>：历史行本就没有这些语义，把缺键当成 0 或当成必填， 都会凭空改变赔付口径。故 {@link #dimension(String)}
 * 缺失时返回 {@code null}，不抛异常。
 */
public record LiabilityRuleJsonView(
    int schemaVersion, Map<String, BigDecimal> amounts, Map<String, String> dimensions) {

  public LiabilityRuleJsonView {
    if (schemaVersion != LiabilityRuleJson.CURRENT_SCHEMA_VERSION) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "liability_rule_json 的 schemaVersion=%d 无法识别（当前仅支持 %d）：" + "未来版本的数据不得按旧语义解释，须先升级读取侧",
          schemaVersion,
          LiabilityRuleJson.CURRENT_SCHEMA_VERSION);
    }
    amounts = normalizeAmounts(amounts);
    dimensions = normalizeDimensions(dimensions);
  }

  /** 历史行（聚合 JSON 为 NULL）：三键全部回落旧标量，不施加任何 B 类约束。 */
  public static LiabilityRuleJsonView empty() {
    return new LiabilityRuleJsonView(LiabilityRuleJson.CURRENT_SCHEMA_VERSION, Map.of(), Map.of());
  }

  /**
   * 按键回落：本视图有该键则取本视图的值，否则取旧标量；两侧都缺则拒绝。
   *
   * @param key {@link LiabilityRuleJson#AMOUNT_KEYS} 之一
   * @param scalar 对应旧标量列的值（可为 {@code null}，表示该列无值）
   * @throws RuleGap 键不属于 A 类，或本视图与该标量同时缺值
   */
  public BigDecimal resolve(String key, BigDecimal scalar) {
    if (!LiabilityRuleJson.AMOUNT_KEYS.contains(key)) {
      throw RuleGap.of(
          RuleGap.Code.INVALID_INPUT,
          "「%s」不是可回落的计算键（合法值：%s）",
          key,
          String.join(" / ", LiabilityRuleJson.AMOUNT_KEYS));
    }
    BigDecimal present = amounts.get(key);
    if (present != null) {
      return present;
    }
    if (scalar != null) {
      return scalar;
    }
    throw RuleGap.of(
        RuleGap.Code.INVALID_INPUT,
        "「%s」在聚合 JSON 中缺失、旧标量列也为 NULL：没有任何真源可回落，" + "按 0 计算会静默少赔，故拒绝读取",
        key);
  }

  /** 该键是否存在于聚合 JSON（供差异定位与断言，不参与金额计算）。 */
  public boolean has(String key) {
    return amounts.containsKey(key);
  }

  /** B 类维度原文；缺失返回 {@code null}，表示"不施加该约束"，不是 0 也不是必填。 */
  public String dimension(String key) {
    return dimensions.get(key);
  }

  private static Map<String, BigDecimal> normalizeAmounts(Map<String, BigDecimal> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, BigDecimal> ordered = new TreeMap<>();
    for (Map.Entry<String, BigDecimal> e : raw.entrySet()) {
      String key = e.getKey();
      if (!LiabilityRuleJson.AMOUNT_KEYS.contains(key)) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "「%s」不是已知的计算键（合法值：%s）：拼错的键不得被静默忽略",
            key,
            String.join(" / ", LiabilityRuleJson.AMOUNT_KEYS));
      }
      if (e.getValue() == null) {
        throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "计算键 %s 的值为空：缺键才表示回落旧标量", key);
      }
      ordered.put(key, e.getValue());
    }
    return Collections.unmodifiableMap(ordered);
  }

  private static Map<String, String> normalizeDimensions(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, String> ordered = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      String key = e.getKey();
      if (!LiabilityRuleJson.DIMENSION_KEYS.contains(key)) {
        throw RuleGap.of(
            RuleGap.Code.INVALID_INPUT,
            "「%s」不是已知的 B 类维度（合法值：%s）：拼错的键不得被静默忽略",
            key,
            String.join(" / ", LiabilityRuleJson.DIMENSION_KEYS));
      }
      if (e.getValue() == null || e.getValue().isBlank()) {
        throw RuleGap.of(RuleGap.Code.INVALID_INPUT, "维度 %s 的值为空：缺键才表示不施加约束", key);
      }
      ordered.put(key, e.getValue());
    }
    return Collections.unmodifiableMap(ordered);
  }
}
