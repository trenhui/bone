package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.QualityReport;
import java.util.List;

/**
 * OCR 质量评估端口（ADR-0046 五层可信度 L3 模型质检 / Jev 类打分模型）。
 *
 * <p>对 OCR 候选结果逐字段打「可信分」，<b>不重写原值</b>。 AUROC 高 ≠ 字段正确率高， 故本端口只提供信号，最终放行由 {@link ReviewPolicy}
 * 综合置信度、质量分、规则与风险等级裁决。 骨架提供 {@code NoOpOcrQualityEvaluator}（返回 none），真实 Jev 适配器后续接入。
 */
public interface OcrQualityEvaluator {

  /** 评估器标识（如 jev / none）。 */
  String evaluatorId();

  /**
   * 评估字段级证据的可信度。
   *
   * @param fields OCR 产出的字段级证据
   * @param docType 文档类型
   * @param sourceRef 来源引用
   * @return 质量评估报告（缺字段分返回 NaN，表示不可用）
   */
  QualityReport evaluate(List<OcrFieldEvidence> fields, OcrDocType docType, String sourceRef);
}
