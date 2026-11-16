package com.bone.claim.domain.model.inspection;

import java.util.Map;

/**
 * 抽检配置解析结果（纯值对象，无 IO）。
 *
 * <p>把 t_feature_toggle 的 config_json 解析与"回落基线"策略收入一个可单测的真源： 比例非法一律回落基线 5（不是 0 也不是
 * 100），并记录被拒字段原因，避免"配了不生效"静默化。
 */
public record InspectionConfig(int samplingRatePercent, Map<String, String> rejected) {

  /** 代码基线：结案案件抽检 ≥5%（监管口径），与 @Value 默认值一致（零行为变更迁移）。 */
  public static final int DEFAULT_SAMPLING_RATE = 5;

  public static InspectionConfig defaults() {
    return new InspectionConfig(DEFAULT_SAMPLING_RATE, Map.of());
  }

  /** 从配置键值对解析比例；任何非法值（类型/越界/未知）回落基线并在 rejected 登记原因。 */
  public static InspectionConfig from(Map<String, Object> raw) {
    if (raw == null || raw.isEmpty()) {
      return defaults();
    }
    Object v = raw.get("samplingRatePercent");
    if (v == null) {
      return new InspectionConfig(
          DEFAULT_SAMPLING_RATE,
          Map.of("samplingRatePercent", "缺键，回落基线 " + DEFAULT_SAMPLING_RATE + "%"));
    }
    int rate;
    try {
      rate = v instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(v).trim());
    } catch (Exception e) {
      return new InspectionConfig(
          DEFAULT_SAMPLING_RATE,
          Map.of("samplingRatePercent", "非整数（" + v + "），回落基线 " + DEFAULT_SAMPLING_RATE + "%"));
    }
    if (rate < 0 || rate > 100) {
      return new InspectionConfig(
          DEFAULT_SAMPLING_RATE,
          Map.of(
              "samplingRatePercent",
              "越界（" + rate + "，合法 0–100），回落基线 " + DEFAULT_SAMPLING_RATE + "%"));
    }
    return new InspectionConfig(rate, Map.of());
  }

  public boolean fullyApplied() {
    return rejected.isEmpty();
  }
}
