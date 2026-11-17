package com.bone.claim.domain.model.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 审核疲劳防控配置（纯值对象，无 IO 依赖）。
 *
 * <p>唯一解析真源来自 {@code REVIEW_FATIGUE_GUARD.config_json}。任何非法值（类型不符、越界、未知键、缺键） 都按字段回落代码基线，并在 {@code
 * rejected} 登记原因——「配了不生效」必须可见，不得静默生效。
 */
public record ReviewFatigueConfig(
    int maxPerShift,
    int restPromptMinutes,
    int shiftEndExclusionMinutes,
    long sensitiveAmountYuan,
    Map<String, String> rejected) {

  public static final int DEFAULT_MAX_PER_SHIFT = 120;
  public static final int DEFAULT_REST_PROMPT_MINUTES = 120;
  public static final int DEFAULT_SHIFT_END_EXCLUSION_MINUTES = 30;
  public static final long DEFAULT_SENSITIVE_AMOUNT_YUAN = 10000L;

  static final String KEY_MAX_PER_SHIFT = "maxPerShift";
  static final String KEY_REST_PROMPT_MINUTES = "restPromptMinutes";
  static final String KEY_SHIFT_END_EXCLUSION_MINUTES = "shiftEndExclusionMinutes";
  static final String KEY_SENSITIVE_AMOUNT_YUAN = "sensitiveAmountYuan";

  private static final Set<String> KNOWN_KEYS =
      Set.of(
          KEY_MAX_PER_SHIFT,
          KEY_REST_PROMPT_MINUTES,
          KEY_SHIFT_END_EXCLUSION_MINUTES,
          KEY_SENSITIVE_AMOUNT_YUAN);

  public static ReviewFatigueConfig defaults() {
    return new ReviewFatigueConfig(
        DEFAULT_MAX_PER_SHIFT,
        DEFAULT_REST_PROMPT_MINUTES,
        DEFAULT_SHIFT_END_EXCLUSION_MINUTES,
        DEFAULT_SENSITIVE_AMOUNT_YUAN,
        Map.of());
  }

  /** 从已解析的键值对构造；缺键 / 非法值 / 未知键都不抛异常，只记入 rejected。 */
  public static ReviewFatigueConfig from(Map<String, Object> raw) {
    if (raw == null || raw.isEmpty()) {
      return defaults();
    }
    Map<String, String> rejected = new LinkedHashMap<>();
    for (String key : raw.keySet()) {
      if (!KNOWN_KEYS.contains(key)) {
        rejected.put(String.valueOf(key), "未知键，未采信（本批不消费该参数）");
      }
    }
    int max = intValue(raw, KEY_MAX_PER_SHIFT, DEFAULT_MAX_PER_SHIFT, 1, 100_000, rejected);
    int rest =
        intValue(raw, KEY_REST_PROMPT_MINUTES, DEFAULT_REST_PROMPT_MINUTES, 1, 24 * 60, rejected);
    int shiftEnd =
        intValue(
            raw,
            KEY_SHIFT_END_EXCLUSION_MINUTES,
            DEFAULT_SHIFT_END_EXCLUSION_MINUTES,
            0,
            12 * 60,
            rejected);
    long amount =
        longValue(
            raw,
            KEY_SENSITIVE_AMOUNT_YUAN,
            DEFAULT_SENSITIVE_AMOUNT_YUAN,
            0L,
            1_000_000_000L,
            rejected);
    return new ReviewFatigueConfig(max, rest, shiftEnd, amount, Map.copyOf(rejected));
  }

  private static int intValue(
      Map<String, Object> raw,
      String key,
      int fallback,
      int min,
      int max,
      Map<String, String> rejected) {
    Object value = raw.get(key);
    if (value == null) {
      rejected.put(key, "缺键，回落基线 " + fallback);
      return fallback;
    }
    long parsed;
    try {
      parsed = new BigDecimal(String.valueOf(value).trim()).longValueExact();
    } catch (Exception e) {
      rejected.put(key, "非整数（" + value + "），回落基线 " + fallback);
      return fallback;
    }
    if (parsed < min || parsed > max) {
      rejected.put(key, "越界（" + value + "，合法 " + min + "–" + max + "），回落基线 " + fallback);
      return fallback;
    }
    return (int) parsed;
  }

  private static long longValue(
      Map<String, Object> raw,
      String key,
      long fallback,
      long min,
      long max,
      Map<String, String> rejected) {
    Object value = raw.get(key);
    if (value == null) {
      rejected.put(key, "缺键，回落基线 " + fallback);
      return fallback;
    }
    long parsed;
    try {
      parsed = new BigDecimal(String.valueOf(value).trim()).longValueExact();
    } catch (Exception e) {
      rejected.put(key, "非整数（" + value + "），回落基线 " + fallback);
      return fallback;
    }
    if (parsed < min || parsed > max) {
      rejected.put(key, "越界（" + value + "，合法 " + min + "–" + max + "），回落基线 " + fallback);
      return fallback;
    }
    return parsed;
  }

  public boolean fullyApplied() {
    return rejected.isEmpty();
  }

  /** 敏感金额只对提示生效，绝不进入理算 / 拒赔 / 支付。 */
  public boolean sensitiveAmountReached(BigDecimal amount) {
    return amount != null && amount.compareTo(BigDecimal.valueOf(sensitiveAmountYuan)) >= 0;
  }
}
