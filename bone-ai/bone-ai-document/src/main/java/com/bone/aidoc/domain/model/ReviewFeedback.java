package com.bone.aidoc.domain.model;

import java.time.Instant;

/**
 * 人工最终裁定反馈（ADR-0046 可信闭环：Human-in-the-loop + 标定真值回流）。
 *
 * <p>一份字段经引擎决策后，人工在理赔系统中最终确认或纠正时产生本记录。 它的价值分两层：
 *
 * <ul>
 *   <li><b>生产准确率度量</b>：{@link #isConfirmedError()} 标识「引擎自动放行但人工判定错误」，
 *       这是衡量自动通过真实质量的核心指标——没有它，「自动通过率」只是放行比例，不含正确性。
 *   <li><b>标定真值</b>：{@code correct} 字段是下一轮 {@code ThresholdCalibrator} 校准阈值的 ground truth。
 * </ul>
 *
 * @param docId 文档标识
 * @param tenantId 租户
 * @param fieldName 字段名
 * @param engineValue 引擎识别值（自动放行时的值）
 * @param humanValue 人工最终值（纠正后；仅确认时可与引擎值一致或为空）
 * @param correct 人工判定引擎值是否正确
 * @param decidedBy 引擎当初给出的复核状态（AUTO_PASSED / SECONDARY_RECOGNITION / MANUAL_REVIEW）
 * @param sourceRef 来源引用（文件 key / URL）
 * @param occurredAt 裁定发生时刻
 */
public record ReviewFeedback(
    String docId,
    String tenantId,
    String fieldName,
    String engineValue,
    String humanValue,
    boolean correct,
    ReviewStatus decidedBy,
    String sourceRef,
    Instant occurredAt) {

  /** 引擎自动放行但人工判定错误：最危险的失察，须优先度量与回溯。 */
  public boolean isConfirmedError() {
    return decidedBy == ReviewStatus.AUTO_PASSED && !correct;
  }
}
