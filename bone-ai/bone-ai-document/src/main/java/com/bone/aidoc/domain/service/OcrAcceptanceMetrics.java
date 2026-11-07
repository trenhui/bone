package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.RiskLevel;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 可信 OCR 验收指标（ADR-0046 PoC 验收口径的可计算化）。
 *
 * <p>所有比率<b>以字段级证据链为准</b>逐条重算，不读 {@code decisionCounts} 聚合值——聚合值若与 证据不一致，本类可作为反查手段（证据是事实，计数是投影）。
 *
 * <p>「关键字段」口径可两种取法：按 {@link ClaimFieldRisk} 推出的 CRITICAL 字段，或由调用方显式 传入字段名集合（走 {@link
 * OcrFieldEvidence#isKeyField(Set)}）。后者用于按票种模板的关键字段 清单做验收，避免口径漂移。
 *
 * @param criticalEvidenceCoverage 关键字段的原图证据定位覆盖率（ADR 验收目标 100%）
 */
public record OcrAcceptanceMetrics(
    int totalFields,
    int autoPassed,
    int manualReview,
    int secondaryRecognition,
    int missingRequiredFields,
    int criticalFields,
    int criticalFieldsWithEvidence,
    int criticalAutoPassed,
    double autoPassRate,
    double manualInterventionRate,
    double criticalEvidenceCoverage) {

  /** 缺字段占位证据的审计标记（与 {@code OcrFieldEvidence.missing} 约定一致）。 */
  private static final String MISSING_MARKER = "[MISSING_REQUIRED_FIELD]";

  /** 按领域风险分级推导关键字段（CRITICAL）。 */
  public static OcrAcceptanceMetrics from(TrustedOcrDocument doc, ClaimFieldRisk fieldRisk) {
    Set<String> keyFields = new LinkedHashSet<>();
    for (OcrFieldEvidence f : doc.fields()) {
      if (fieldRisk.levelOf(doc.docType(), f.fieldName()) == RiskLevel.CRITICAL) {
        keyFields.add(f.fieldName());
      }
    }
    return from(doc, keyFields);
  }

  /** 按显式关键字段清单验收（票种模板可直接提供该清单）。 */
  public static OcrAcceptanceMetrics from(TrustedOcrDocument doc, Set<String> keyFieldNames) {
    int auto = 0;
    int manual = 0;
    int secondary = 0;
    int missing = 0;
    int critical = 0;
    int criticalLocated = 0;
    int criticalAuto = 0;
    for (OcrFieldEvidence f : doc.fields()) {
      switch (f.reviewStatus()) {
        case AUTO_PASSED -> auto++;
        case MANUAL_REVIEW -> manual++;
        case SECONDARY_RECOGNITION -> secondary++;
        default -> {
          // PENDING 不计入任何处置分布，保留原样以便发现未路由字段
        }
      }
      if (f.rawText() != null && f.rawText().startsWith(MISSING_MARKER)) {
        missing++;
      }
      if (f.isKeyField(keyFieldNames)) {
        critical++;
        if (f.hasEvidenceLocation()) {
          criticalLocated++;
        }
        if (f.reviewStatus() == ReviewStatus.AUTO_PASSED) {
          criticalAuto++;
        }
      }
    }
    int total = doc.fields().size();
    return new OcrAcceptanceMetrics(
        total,
        auto,
        manual,
        secondary,
        missing,
        critical,
        criticalLocated,
        criticalAuto,
        total == 0 ? 0.0 : (double) auto / total,
        total == 0 ? 0.0 : (double) (manual + secondary) / total,
        critical == 0 ? 0.0 : (double) criticalLocated / critical);
  }

  /** 是否满足「关键字段 100% 证据定位覆盖」（无关键字段时判不达标，避免空集假绿）。 */
  public boolean meetsKeyFieldEvidenceCoverageTarget() {
    return criticalFields > 0 && criticalFieldsWithEvidence == criticalFields;
  }
}
