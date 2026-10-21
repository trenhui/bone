package com.bone.ailedger.domain.model;

/**
 * 台账操作的机器编码结论（借鉴 pk-tpa-saas {@code AdjustmentDetailFailReasonEnum} 的编码化结论模式）。
 *
 * <p>每次台账操作都返回一个稳定、可聚合、可 i18n 的码，而非只有自由文本—— 下游「审核路由 / 监控告警 / 前端 i18n / 规则回流」只认机器码：
 * 按中文文案聚合会随措辞漂移、英文用户看到中文。{@code code} 是跨系统契约与 {@code i18n.t('ledger.reason.'+code)} 的键。
 */
public enum LimitVerdictReason {

  /** 预留成功（额度已持有，待 commit / release）。 */
  RESERVED("预留成功"),

  /** 提交成功（额度已实际占用）。 */
  COMMITTED("提交成功，额度已实际占用"),

  /** 释放成功（预留作废，额度已返还）。 */
  RELEASED("释放成功，额度已返还"),

  /** 冻结成功（额度已冻结，可用额减少但未占用）。 */
  FROZEN("冻结成功"),

  /** 解冻成功（冻结额返还可用）。 */
  THAWED("解冻成功"),

  /** 限额已登记 / 更新。 */
  LIMIT_DECLARED("限额已登记"),

  /** 可用额度不足（含冻结时可用不够）——业务侧最常见的编码结论。 */
  LIMIT_EXCEEDED("可用额度不足"),

  /** 预留不存在或已结算（commit / release 的目标无效）。 */
  RESERVATION_NOT_FOUND("预留不存在或已结算"),

  /** 金额非法（空 / 非正数）或预留标识为空。 */
  AMOUNT_INVALID("金额或预留标识非法");

  public final String desc;

  LimitVerdictReason(String desc) {
    this.desc = desc;
  }
}
