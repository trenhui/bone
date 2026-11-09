package com.bone.claim.domain.model.liability;

import java.math.BigDecimal;

/**
 * 一条双轨比对差异（M2 ③ 3a）。
 *
 * <p><b>它只被记录，不被用于计算</b>：{@link #legacyValue()} 是本次请求实际返回的金额来源， {@link #jsonValue()} 是按聚合 JSON
 * 按键回落后的值。两者不等时由应用层写结构化日志与计数—— 这正是后续判断"能否切主"的唯一证据来源，所以两个值都要留在同一条记录里，事后单看一个值无法复盘。
 *
 * <p><b>为什么带 {@code liabilityUuid} 与 {@code liabilityVersion}</b>：差异必须能定位到"哪一版责任配置"。
 * 只记数值不记版本，事后无法区分"配置写错了"与"读路径算错了"，而这两种原因的处理方式完全相反。
 *
 * @param liabilityUuid 责任 UUID（同名多版本）
 * @param liabilityVersion 责任版本（差异归属的最小可定位单元）
 * @param key 差异键（{@link LiabilityRuleJson#AMOUNT_KEYS} 之一）
 * @param legacyValue 旧标量列的值（本次请求真正采用的金额来源）
 * @param jsonValue 聚合 JSON 按键回落后的值（仅用于比对）
 */
public record RuleValueDiff(
    String liabilityUuid,
    String liabilityVersion,
    String key,
    BigDecimal legacyValue,
    BigDecimal jsonValue) {

  /** 供日志与指标使用的稳定标签，避免调用方各自拼字符串导致口径漂移。 */
  public String label() {
    return key + "@" + liabilityUuid + "#" + liabilityVersion;
  }
}
