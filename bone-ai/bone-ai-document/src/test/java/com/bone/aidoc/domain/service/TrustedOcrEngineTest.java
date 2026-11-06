package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.aidoc.domain.gateway.OcrProvider;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.RecognizedField;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link TrustedOcrEngine} 七层编排 + 字段级路由断言（纯域，无模型调用）。 */
class TrustedOcrEngineTest {

  private static final class StubProvider implements OcrProvider {
    private final OcrDocType type;

    StubProvider(OcrDocType type) {
      this.type = type;
    }

    @Override
    public String engineId() {
      return "stub";
    }

    @Override
    public boolean supports(OcrDocType docType) {
      return docType == type;
    }

    @Override
    public OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType) {
      List<RecognizedField> fields =
          List.of(
              new RecognizedField("total_amount", "100.00", 0.999),
              new RecognizedField("hospital_name", "某医院", 0.99));
      return new OcrResult("stub", docType, fields, List.of(), "raw", 1, 0.99);
    }
  }

  private TrustedOcrEngine engineWith(OcrProvider provider) {
    return new TrustedOcrEngine(
        List.of(provider),
        new NoOpOcrQualityEvaluator(),
        new DefaultReviewPolicy(),
        new ClaimFieldRisk());
  }

  @Test
  void routes_critical_to_secondary_and_low_to_auto_without_quality() {
    TrustedOcrDocument doc =
        engineWith(new StubProvider(OcrDocType.INVOICE))
            .parse(InputStream.nullInputStream(), "t1", "inv.jpg", "doc-1", OcrDocType.INVOICE);

    assertThat(doc.fields()).hasSize(2);
    assertThat(doc.primaryEngineId()).isEqualTo("stub");
    assertThat(doc.docId()).isEqualTo("doc-1");

    OcrFieldEvidence amount =
        doc.fields().stream()
            .filter(f -> f.fieldName().equals("total_amount"))
            .findFirst()
            .orElseThrow();
    // CRITICAL + 无质量分 → 二次识别
    assertThat(amount.reviewStatus()).isEqualTo(ReviewStatus.SECONDARY_RECOGNITION);

    OcrFieldEvidence hospital =
        doc.fields().stream()
            .filter(f -> f.fieldName().equals("hospital_name"))
            .findFirst()
            .orElseThrow();
    // LOW + 高置信 → 自动通过
    assertThat(hospital.reviewStatus()).isEqualTo(ReviewStatus.AUTO_PASSED);

    assertThat(doc.decisionCounts().get(ReviewStatus.SECONDARY_RECOGNITION)).isEqualTo(1);
    assertThat(doc.decisionCounts().get(ReviewStatus.AUTO_PASSED)).isEqualTo(1);
  }

  @Test
  void no_supporting_provider_is_config_error_not_silent_empty() {
    TrustedOcrEngine engine = engineWith(new StubProvider(OcrDocType.INVOICE));
    assertThatThrownBy(
            () ->
                engine.parse(
                    InputStream.nullInputStream(), "t1", "x.jpg", "d1", OcrDocType.ID_CARD))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void auto_generates_doc_id_when_blank() {
    TrustedOcrDocument doc =
        engineWith(new StubProvider(OcrDocType.INVOICE))
            .parse(InputStream.nullInputStream(), "t1", "inv.jpg", "  ", OcrDocType.INVOICE);
    assertThat(doc.docId()).isNotBlank();
  }
}
