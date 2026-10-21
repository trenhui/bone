package com.bone.ailedger.domain.model;

/**
 * 限额维度范围（域中立）：{@code dimension} 是产品自定义的限额维度，{@code scopeKey} 是该维度下的具体对象键。
 *
 * <p>维度由产品域定义，内核不预设业务语义。以 TPA 理赔为例，产品可定义 {@code ("LIABILITY","clause-42")}（责任-条款）、 {@code
 * ("PERSONAL","user-1001")}（个人年度）、{@code ("HOSPITAL","H001")}（按医院）等—— 维度名与键的含义归产品， 内核只负责按 {@code
 * (dimension, scopeKey)} 聚合记账。
 */
public record LimitScope(String dimension, String scopeKey) {

  public LimitScope {
    if (dimension == null || dimension.isBlank()) {
      throw new IllegalArgumentException("限额维度不可为空");
    }
    if (scopeKey == null || scopeKey.isBlank()) {
      throw new IllegalArgumentException("限额范围键不可为空");
    }
  }

  public static LimitScope of(String dimension, String scopeKey) {
    return new LimitScope(dimension, scopeKey);
  }
}
