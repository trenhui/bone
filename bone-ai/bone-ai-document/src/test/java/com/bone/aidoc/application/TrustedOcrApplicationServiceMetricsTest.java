package com.bone.aidoc.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.gateway.OcrEngine;
import com.bone.aidoc.domain.gateway.RecordingFeedbackCollector;
import com.bone.aidoc.domain.gateway.RecordingOcrMetrics;
import com.bone.aidoc.domain.model.BoundingBox;
import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.ReviewFeedback;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import com.bone.aidoc.domain.model.ValidationStatus;
import com.bone.aidoc.domain.service.ClaimDocumentTemplateRegistry;
import com.bone.aidoc.domain.service.ClaimFieldRisk;
import com.bone.aidoc.domain.service.NoOpFeedbackCollector;
import com.bone.aidoc.domain.service.NoOpOcrMetrics;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 应用层可信闭环：解析后上报度量 + 反馈采集入口。 */
class TrustedOcrApplicationServiceMetricsTest {

  private static OcrFieldEvidence field(
      String name, ReviewStatus status, ValidationStatus v, String reason) {
    OcrFieldEvidence e =
        new OcrFieldEvidence(
            name,
            "v",
            null,
            1,
            new BoundingBox(0, 0, 10, 10),
            "eng",
            0.999,
            ConfidenceSource.OCR_ENGINE,
            v,
            status);
    return e;
  }

  private static TrustedOcrDocument doc(
      Map<ReviewStatus, Integer> counts, Map<String, String> reasons) {
    OcrFieldEvidence total =
        field("total_amount", ReviewStatus.AUTO_PASSED, ValidationStatus.PASSED, "[放行]");
    OcrFieldEvidence no =
        field("invoice_no", ReviewStatus.MANUAL_REVIEW, ValidationStatus.FAILED, "[L2] 规则失败");
    return new TrustedOcrDocument(
        "d1",
        "t1",
        "ref",
        OcrDocType.INVOICE,
        "eng",
        List.of("eng"),
        List.of(total, no),
        com.bone.aidoc.domain.model.QualityReport.none("none"),
        "raw",
        1,
        counts,
        reasons);
  }

  private static final class StubEngine implements OcrEngine {
    private final TrustedOcrDocument doc;

    StubEngine(TrustedOcrDocument doc) {
      this.doc = doc;
    }

    @Override
    public TrustedOcrDocument parse(
        InputStream input, String tenantId, String sourceRef, String docId, OcrDocType docType) {
      return doc;
    }
  }

  @Test
  void parse_reports_metrics_and_classification() {
    Map<ReviewStatus, Integer> counts = new EnumMap<>(ReviewStatus.class);
    counts.put(ReviewStatus.AUTO_PASSED, 1);
    counts.put(ReviewStatus.MANUAL_REVIEW, 1);
    Map<String, String> reasons = new java.util.LinkedHashMap<>();
    reasons.put("total_amount", "[放行] autoPassConfidence=0.99 autoPassQuality=0.95");
    reasons.put("invoice_no", "[L2] 确定性规则校验失败");

    RecordingOcrMetrics metrics = new RecordingOcrMetrics();
    TrustedOcrApplicationService svc =
        new TrustedOcrApplicationService(
            new StubEngine(doc(counts, reasons)),
            metrics,
            new NoOpFeedbackCollector(),
            new ClaimFieldRisk(ClaimDocumentTemplateRegistry.builtIn()));

    svc.parse(new ByteArrayInputStream(new byte[] {1}), "t1", "ref", "d1", OcrDocType.INVOICE);

    assertThat(metrics.decisionsOf(ReviewStatus.AUTO_PASSED)).isEqualTo(1);
    assertThat(metrics.decisionsOf(ReviewStatus.MANUAL_REVIEW)).isEqualTo(1);
    assertThat(metrics.snapshot().criticalAutoPassed())
        .isEqualTo(1); // total_amount 在 INVOICE 模板中为 CRITICAL
    assertThat(metrics.snapshot().l4Mismatch()).isZero();
    assertThat(metrics.snapshot().safetyGates()).isEqualTo(1); // invoice_no 被 [L2] 闸门拦下
    assertThat(metrics.classifications()).isEqualTo(1);
  }

  @Test
  void record_feedback_is_forwarded_to_collector() {
    RecordingFeedbackCollector collector = new RecordingFeedbackCollector();
    TrustedOcrApplicationService svc =
        new TrustedOcrApplicationService(
            new StubEngine(doc(new EnumMap<>(ReviewStatus.class), Map.of())),
            new NoOpOcrMetrics(),
            collector,
            new ClaimFieldRisk(ClaimDocumentTemplateRegistry.builtIn()));

    ReviewFeedback confirmed =
        new ReviewFeedback(
            "d1",
            "t1",
            "total_amount",
            "200.00",
            "100.00",
            false,
            ReviewStatus.AUTO_PASSED,
            "ref",
            java.time.Instant.now());
    svc.recordFeedback(confirmed);

    assertThat(collector.recorded()).hasSize(1);
    assertThat(collector.confirmedErrors()).isEqualTo(1);
  }

  @Test
  void null_feedback_is_ignored() {
    RecordingFeedbackCollector collector = new RecordingFeedbackCollector();
    TrustedOcrApplicationService svc =
        new TrustedOcrApplicationService(
            new StubEngine(doc(new EnumMap<>(ReviewStatus.class), Map.of())),
            new NoOpOcrMetrics(),
            collector,
            new ClaimFieldRisk(ClaimDocumentTemplateRegistry.builtIn()));
    svc.recordFeedback(null);
    assertThat(collector.recorded()).isEmpty();
  }
}
