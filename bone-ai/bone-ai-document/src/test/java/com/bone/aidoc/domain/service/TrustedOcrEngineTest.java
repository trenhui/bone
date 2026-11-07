package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.aidoc.domain.gateway.OcrProvider;
import com.bone.aidoc.domain.model.BoundingBox;
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

  private static final class MultiPageProvider implements OcrProvider {
    @Override
    public String engineId() {
      return "stub";
    }

    @Override
    public boolean supports(OcrDocType docType) {
      return docType == OcrDocType.FEE_LIST;
    }

    @Override
    public OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType) {
      List<RecognizedField> fields =
          List.of(
              // 第 3 页、带原图定位框的字段
              new RecognizedField(
                  "total_amount", "880.00", 0.999, 3, new BoundingBox(10, 20, 120, 40)),
              // 第 1 页、无坐标字段
              new RecognizedField("hospital_name", "某医院", 0.99, 1, null));
      return new OcrResult("stub", docType, fields, List.of(), "raw", 3, 0.99);
    }
  }

  /** 本类聚焦字段路由本身，显式关闭票种模板以隔离 Phase-4 的漏字段拦截与引擎重排。 */
  private TrustedOcrEngine engineWith(OcrProvider provider) {
    ClaimDocumentTemplateRegistry none = ClaimDocumentTemplateRegistry.noTemplates();
    return new TrustedOcrEngine(
        List.of(provider),
        new NoOpOcrQualityEvaluator(),
        new DefaultReviewPolicy(),
        new ClaimFieldRisk(none),
        List.of(),
        null,
        none);
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

  @Test
  void propagates_per_field_page_and_bbox_from_recognized_field() {
    TrustedOcrDocument doc =
        engineWith(new MultiPageProvider())
            .parse(InputStream.nullInputStream(), "t1", "fee.pdf", "doc-mp", OcrDocType.FEE_LIST);

    assertThat(doc.pageCount()).isEqualTo(3);
    OcrFieldEvidence amount =
        doc.fields().stream()
            .filter(f -> f.fieldName().equals("total_amount"))
            .findFirst()
            .orElseThrow();
    // 字段自带页码与坐标被透传，而非统一回退为 1
    assertThat(amount.page()).isEqualTo(3);
    assertThat(amount.hasEvidenceLocation()).isTrue();

    OcrFieldEvidence hospital =
        doc.fields().stream()
            .filter(f -> f.fieldName().equals("hospital_name"))
            .findFirst()
            .orElseThrow();
    assertThat(hospital.page()).isEqualTo(1);
    assertThat(hospital.hasEvidenceLocation()).isFalse();
  }
}
