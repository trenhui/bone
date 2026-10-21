package com.bone.ailedger.domain.model;

import java.math.BigDecimal;

/** 限额规则（维度范围 + 上限值）：产品域为某维度登记的上限，台账据此判定可用额度。 */
public record LimitRule(LimitScope scope, BigDecimal limit) {

  public LimitRule {
    if (scope == null) {
      throw new IllegalArgumentException("限额规则的范围不可为空");
    }
    if (limit == null || limit.signum() < 0) {
      throw new IllegalArgumentException("限额上限不可为空或负值: " + limit);
    }
  }

  public static LimitRule of(LimitScope scope, BigDecimal limit) {
    return new LimitRule(scope, limit);
  }
}
