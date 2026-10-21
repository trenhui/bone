package com.bone.aiserver.claim;

/**
 * 理赔决策的机器编码原因（TPA「编码化结论」模式，借鉴 pk-tpa-saas 理算引擎的 {@code AdjustmentDetailFailReasonEnum} / {@code
 * BizErrorCode}：每一次核赔结果都带一个稳定、可聚合、可 i18n 的码，而非只有自由文本）。
 *
 * <p><b>为什么需要</b>：亿保 / 普康等 TPA 的智能核赔要把结论喂给「审核路由 / 监控告警 / 前端 i18n / 规则回流」， 这些下游只认机器码——
 * 按中文文案聚合会随措辞漂移、英文用户看到中文。{@code code} 是跨系统契约与 {@code i18n.t('claim.reason.'+code)} 的键，{@code desc}
 * 仅供人读。
 *
 * <p>与「AI 只产证据与建议、确定性路径裁决」一致：这里的码全部由确定性信号（规则短路 / 置信度分档 / 风险等级 / AI 可用性）派生， 不含 AI 自由发挥。
 */
public enum ClaimReason {

  /** 规则命中免责 / 风控 → 成本红线短路，未调用 LLM（确定性路径直接拒赔）。 */
  RULE_EXCLUDED("规则命中免责或风控，短路未调用模型"),

  /** AI 服务不可用（超时 / 熔断 / provider 故障）→ 转确定性或人工路径，绝不阻断业务。 */
  AI_UNAVAILABLE("模型服务不可用，转人工复核"),

  /** 高风险案件（riskLevel=HIGH）→ 强制人工复核，与置信度无关。 */
  HIGH_RISK("高风险案件，强制人工复核"),

  /** 超出理赔额度上限（确定性额度内核判定）→ 转人工裁定超额部分，绝不自动放行。 */
  LIMIT_EXCEEDED("超出理赔额度上限，需人工裁定超额部分"),

  /** 置信度低于强制人工阈值 → 强制人工复核。 */
  CONFIDENCE_LOW("置信度低于强制人工阈值，强制人工复核"),

  /** 置信度处于复核区间（未达自动通过线）→ 需人工复核。 */
  CONFIDENCE_REVIEW("置信度处于复核区间，需人工复核"),

  /** 高置信度且非高风险 → 自动通过。 */
  AUTO_APPROVED("高置信度且非高风险，自动通过");

  public final String desc;

  ClaimReason(String desc) {
    this.desc = desc;
  }
}
