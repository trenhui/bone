package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

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

/** {@link TrustedOcrEngine} L3 交叉校验路径：金额分歧 → 强制人工复核。 */
class TrustedOcrEngineCrossTest {

  private static final class Primary implements OcrProvider {
    @Override
    public String engineId() {
      return "paddle-ocr";
    }

    @Override
    public boolean supports(OcrDocType d) {
      return d == OcrDocType.INVOICE;
    }

    @Override
    public OcrResult recognize(InputStream in, String ref, OcrDocType d) {
      return new OcrResult(
          "paddle-ocr",
          d,
          List.of(
              new RecognizedField("total_amount", "100.00", 0.999),
              new RecognizedField("hospital_name", "某医院", 0.99)),
          List.of(),
          "raw",
          1,
          0.99);
    }
  }

  private static final class Secondary implements OcrProvider {
    @Override
    public String engineId() {
      return "glm-ocr";
    }

    @Override
    public boolean supports(OcrDocType d) {
      return d == OcrDocType.INVOICE;
    }

    @Override
    public OcrResult recognize(InputStream in, String ref, OcrDocType d) {
      return new OcrResult(
          "glm-ocr",
          d,
          List.of(
              // 金额与主引擎分歧 → 应升级人工复核
              new RecognizedField("total_amount", "200.00", 0.98),
              new RecognizedField("hospital_name", "某医院", 0.98)),
          List.of(),
          "raw2",
          1,
          0.98);
    }
  }

  @Test
  void mismatch_escalates_amount_to_manual_even_without_quality() {
    // 本类聚焦交叉校验本身，显式关闭票种模板以隔离 Phase-4 的漏字段拦截
    ClaimDocumentTemplateRegistry none = ClaimDocumentTemplateRegistry.noTemplates();
    TrustedOcrEngine engine =
        new TrustedOcrEngine(
            List.of(new Primary()),
            new NoOpOcrQualityEvaluator(),
            new DefaultReviewPolicy(),
            new ClaimFieldRisk(none),
            List.of(),
            new Secondary(),
            none);

    TrustedOcrDocument doc =
        engine.parse(InputStream.nullInputStream(), "t1", "inv.jpg", "doc-x", OcrDocType.INVOICE);

    OcrFieldEvidence amount =
        doc.fields().stream()
            .filter(f -> f.fieldName().equals("total_amount"))
            .findFirst()
            .orElseThrow();
    // 金额 CRITICAL + 无质量分 + 交叉分歧 → 人工复核（而非二次识别）
    assertThat(amount.reviewStatus()).isEqualTo(ReviewStatus.MANUAL_REVIEW);

    OcrFieldEvidence hospital =
        doc.fields().stream()
            .filter(f -> f.fieldName().equals("hospital_name"))
            .findFirst()
            .orElseThrow();
    // 医院名一致且低风险 → 自动通过
    assertThat(hospital.reviewStatus()).isEqualTo(ReviewStatus.AUTO_PASSED);
  }
}
