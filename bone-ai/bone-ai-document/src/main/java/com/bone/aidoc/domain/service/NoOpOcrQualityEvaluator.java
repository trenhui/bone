package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.OcrQualityEvaluator;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.QualityReport;
import java.util.List;

/**
 * 占位质量评估器（无质量模型时的安全默认）：返回 {@link QualityReport#none(String)}。
 *
 * <p>质量分不可用（NaN）时，{@code DefaultReviewPolicy} 对 CRITICAL 字段强制转二次识别， 不会误自动放行。真实 Jev 适配器接入后由基础设施层以
 * {@code @Bean} 覆盖本实现。
 */
public class NoOpOcrQualityEvaluator implements OcrQualityEvaluator {

  @Override
  public String evaluatorId() {
    return "none";
  }

  @Override
  public QualityReport evaluate(
      List<OcrFieldEvidence> fields, OcrDocType docType, String sourceRef) {
    return QualityReport.none("none");
  }
}
