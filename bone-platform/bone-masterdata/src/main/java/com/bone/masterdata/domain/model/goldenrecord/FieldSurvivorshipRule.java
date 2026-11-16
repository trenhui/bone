package com.bone.masterdata.domain.model.goldenrecord;

import com.bone.core.exception.DomainException;

/** 单字段胜出策略。字段 Owner 由治理组织在平台侧配置，域侧不得写死。 */
public final class FieldSurvivorshipRule {

  public enum Strategy {
    /** 按来源可信度排序。 */
    SOURCE_PRIORITY,
    /** 按观测时间取最新。 */
    RECENCY,
    /** 具名数据管家指定源记录，优先级最高。 */
    STEWARD_OVERRIDE,
    /** 不允许自动合并，必须转人工。 */
    MANUAL_REVIEW
  }

  private final String fieldCode;
  private final Strategy strategy;

  private FieldSurvivorshipRule(String fieldCode, Strategy strategy) {
    this.fieldCode = fieldCode;
    this.strategy = strategy;
  }

  public static FieldSurvivorshipRule of(String fieldCode, Strategy strategy) {
    if (fieldCode == null || fieldCode.trim().isEmpty() || strategy == null) {
      throw new DomainException("字段胜出策略缺少 fieldCode 或 strategy");
    }
    return new FieldSurvivorshipRule(fieldCode, strategy);
  }

  public String getFieldCode() {
    return fieldCode;
  }

  public Strategy getStrategy() {
    return strategy;
  }

  public String ruleId() {
    return strategy.name() + ":" + fieldCode;
  }
}
