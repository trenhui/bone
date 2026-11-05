package com.bone.claim.domain.model;

import java.math.BigDecimal;

/**
 * 理赔域金额阈值（单一真源）。
 *
 * <p><b>为什么存在</b>：方案 §11.4 要求阈值「经 feature_toggle 外置，改阈值不发版」。本类持有各金额阈值的 <b>代码基线</b>，所有需要「1
 * 万」这个业务边界的地方——决策分诊的金额闸、结算单双人复核门、案件双人复核前置—— 都必须经本类取数，<b>禁止在各自类里再写一份 {@code 10000}</b>。否则改一处另一处静默失效：
 * {@code SettlementOrder#reviewThreshold()} 的注释已记录过这个坑（阈值调高了，巡检还在按旧门判，报一片假红）。
 *
 * <p><b>外置覆盖的分层</b>：
 *
 * <ul>
 *   <li>决策分诊层——经 {@code TriageLimits} 在基线之上套 feature_toggle 覆盖（只收紧不放开，见其类注释）； {@code
 *       TriageLimits.defaults()} 与 {@code DecisionTriage.AUTO_AMOUNT_LIMIT} 都回指本基线。
 *   <li>域模型（结算 / 案件）——无 Spring 上下文，直接使用本基线常量；若后续要把双审门也做成运行时可配， 应在<b>应用层</b>解析 feature_toggle
 *       后将生效值传入域操作，而非在域里读配置。
 * </ul>
 */
public final class ClaimThresholds {

  /** 双人复核 / 不自动通过的金额门（元，≥ 即进入复核或双审档）。基线 1 万元。 */
  public static final BigDecimal DUAL_REVIEW_AMOUNT = new BigDecimal("10000");

  private ClaimThresholds() {}
}
