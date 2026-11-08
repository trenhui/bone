package com.bone.claim.domain.model.adjustment;

/**
 * 理算版本链（§9.4「全版本链，回放与复算必需」中理算侧尚缺的五个环境维度）。
 *
 * <p><b>为什么是值对象而不是五个散参</b>：{@code Adjustment.calculate} 的重载链已到十一参， 再散加五个参数必然错位；且这五个值同源（决策执行 +
 * 案件计划）、同生命周期（受理时刻锁定）， 是一个不可分割的"环境指纹"。
 *
 * <p><b>空串语义（与 0100 迁移、claim_decision_execution 同口径）</b>：空串 =「该维度未参与本次理算」
 * ——决策图未跑（人工直受理算）、案件无计划版本时各维度即为空串；<b>不用 null</b>， 避免读侧判空（DDL 为 {@code NOT NULL DEFAULT ''}）。
 */
public record CalculationVersions(
    String planVersion,
    String catalogVersion,
    String modelVersion,
    String promptVersion,
    String graphVersion) {

  /** 全维度未参与（未跑决策图 / 无计划版本等）：各维度落空串，而非 null。 */
  public static final CalculationVersions NONE =
      new CalculationVersions(null, null, null, null, null);

  public CalculationVersions {
    planVersion = orEmpty(planVersion);
    catalogVersion = orEmpty(catalogVersion);
    modelVersion = orEmpty(modelVersion);
    promptVersion = orEmpty(promptVersion);
    graphVersion = orEmpty(graphVersion);
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }
}
