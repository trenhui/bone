package com.bone.masterdata.domain.model.goldenrecord;

import com.bone.masterdata.domain.model.goldenrecord.FieldSurvivorshipRule.Strategy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 字段级胜出策略集；未显式配置的字段默认 SOURCE_PRIORITY，不做隐式人工合并。 */
public final class SurvivorshipPolicy {

  private final Map<String, FieldSurvivorshipRule> rules;

  private SurvivorshipPolicy(Map<String, FieldSurvivorshipRule> rules) {
    this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
  }

  public static SurvivorshipPolicy of(Map<String, FieldSurvivorshipRule> rules) {
    return new SurvivorshipPolicy(rules == null ? Map.of() : rules);
  }

  public FieldSurvivorshipRule ruleFor(String fieldCode) {
    return rules.getOrDefault(
        fieldCode, FieldSurvivorshipRule.of(fieldCode, Strategy.SOURCE_PRIORITY));
  }

  public Map<String, FieldSurvivorshipRule> getRules() {
    return rules;
  }
}
