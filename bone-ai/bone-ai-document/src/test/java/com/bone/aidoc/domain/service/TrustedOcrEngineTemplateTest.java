package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.gateway.DocumentClassifier;
import com.bone.aidoc.domain.gateway.OcrProvider;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.RecognizedField;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Phase-4：必填字段漏识别拦截、按票种引擎路由、文档分类器可选接入。 */
class TrustedOcrEngineTemplateTest {

  private static final class FieldsProvider implements OcrProvider {
    private final String engineId;
    private final List<RecognizedField> fields;

    FieldsProvider(String engineId, RecognizedField... fields) {
      this.engineId = engineId;
      this.fields = List.of(fields);
    }

    @Override
    public String engineId() {
      return engineId;
    }

    @Override
    public boolean supports(OcrDocType docType) {
      return true;
    }

    @Override
    public OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType) {
      return new OcrResult(engineId, docType, fields, List.of(), "raw", 1, 0.99);
    }
  }

  private static TrustedOcrEngine engine(
      ClaimDocumentTemplateRegistry templates,
      DocumentClassifier classifier,
      OcrProvider... providers) {
    return new TrustedOcrEngine(
        List.of(providers),
        new NoOpOcrQualityEvaluator(),
        new DefaultReviewPolicy(),
        new ClaimFieldRisk(templates),
        List.of(),
        null,
        templates,
        classifier);
  }

  private static TrustedOcrDocument parse(
      TrustedOcrEngine engine, OcrDocType type, OcrProvider provider) {
    return engine.parse(
        new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}), "t1", "src.jpg", "d1", type);
  }

  private static OcrProvider invoiceWithTwoFields() {
    return new FieldsProvider(
        "stub",
        new RecognizedField("total_amount", "100.00", 0.999),
        new RecognizedField("hospital_name", "某医院", 0.99));
  }

  @Test
  void missing_required_fields_are_not_silently_dropped() {
    OcrProvider provider = invoiceWithTwoFields();
    TrustedOcrDocument withTemplate =
        parse(
            engine(ClaimDocumentTemplateRegistry.builtIn(), null, provider),
            OcrDocType.INVOICE,
            provider);
    // 修复前对照：无模板时漏字段完全不可见，自动通过率被虚高计算
    TrustedOcrDocument withoutTemplate =
        parse(
            engine(ClaimDocumentTemplateRegistry.noTemplates(), null, provider),
            OcrDocType.INVOICE,
            provider);

    assertThat(withoutTemplate.fields()).hasSize(2);
    assertThat(withTemplate.fields()).hasSize(4);
    assertThat(withTemplate.autoPassRate()).isLessThan(withoutTemplate.autoPassRate());

    List<OcrFieldEvidence> missing =
        withTemplate.fields().stream()
            .filter(f -> f.rawText() != null && f.rawText().startsWith("[MISSING_REQUIRED_FIELD]"))
            .collect(Collectors.toList());
    assertThat(missing)
        .extracting(OcrFieldEvidence::fieldName)
        .containsExactlyInAnyOrder("invoice_no", "invoice_date");
    assertThat(missing)
        .allSatisfy(
            m -> {
              assertThat(m.value()).isEmpty();
              assertThat(m.confidence()).isZero();
              assertThat(m.validationStatus()).isEqualTo(ValidationStatus.FAILED);
              assertThat(m.reviewStatus()).isEqualTo(ReviewStatus.MANUAL_REVIEW);
            });
    assertThat(withTemplate.decisionCounts().get(ReviewStatus.MANUAL_REVIEW)).isEqualTo(2);
    assertThat(withTemplate.needsHumanAttention())
        .isGreaterThan(withoutTemplate.needsHumanAttention());
  }

  @Test
  void chinese_aliases_do_not_trigger_false_missing_alerts() {
    OcrProvider provider =
        new FieldsProvider(
            "stub",
            new RecognizedField("合计金额", "100.00", 0.999),
            new RecognizedField("发票号码", "FP1", 0.99),
            new RecognizedField("开票日期", "2026-01-01", 0.99));
    TrustedOcrDocument doc =
        parse(
            engine(ClaimDocumentTemplateRegistry.builtIn(), null, provider),
            OcrDocType.INVOICE,
            provider);
    assertThat(doc.fields()).hasSize(3);
    assertThat(doc.fields()).noneMatch(f -> f.validationStatus() == ValidationStatus.FAILED);
  }

  @Test
  void free_text_doc_type_does_not_invent_missing_fields() {
    OcrProvider provider =
        new FieldsProvider("stub", new RecognizedField("diagnosis", "上呼吸道感染", 0.99));
    TrustedOcrDocument doc =
        parse(
            engine(ClaimDocumentTemplateRegistry.builtIn(), null, provider),
            OcrDocType.MEDICAL_RECORD,
            provider);
    assertThat(doc.fields()).hasSize(1);
    assertThat(doc.fields().get(0).reviewStatus()).isEqualTo(ReviewStatus.AUTO_PASSED);
  }

  @Test
  void engine_routing_follows_doc_type_template() {
    OcrProvider glm = new FieldsProvider("glm-ocr", new RecognizedField("note", "a", 0.99));
    OcrProvider paddle = new FieldsProvider("paddle-ocr", new RecognizedField("note", "b", 0.99));
    // 装配顺序 glm 在前，模板须把结构化票种改判为 paddle 优先
    TrustedOcrEngine engine = engine(ClaimDocumentTemplateRegistry.builtIn(), null, glm, paddle);

    assertThat(parse(engine, OcrDocType.INVOICE, glm).primaryEngineId()).isEqualTo("paddle-ocr");
    assertThat(parse(engine, OcrDocType.MEDICAL_RECORD, glm).primaryEngineId())
        .isEqualTo("glm-ocr");
  }

  @Test
  void classifier_runs_only_when_doc_type_is_general() {
    AtomicInteger calls = new AtomicInteger();
    DocumentClassifier classifier =
        (input, sourceRef) -> {
          calls.incrementAndGet();
          return OcrDocType.INVOICE;
        };
    OcrProvider provider =
        new FieldsProvider("stub", new RecognizedField("total_amount", "100.00", 0.999));
    TrustedOcrEngine engine =
        engine(ClaimDocumentTemplateRegistry.noTemplates(), classifier, provider);

    TrustedOcrDocument general = parse(engine, OcrDocType.GENERAL, provider);
    assertThat(general.docType()).isEqualTo(OcrDocType.INVOICE);
    assertThat(calls.get()).isEqualTo(1);

    TrustedOcrDocument explicit = parse(engine, OcrDocType.FEE_LIST, provider);
    assertThat(explicit.docType()).isEqualTo(OcrDocType.FEE_LIST);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void noop_classifier_keeps_general_doc_type_safe() {
    OcrProvider provider = new FieldsProvider("stub", new RecognizedField("note", "x", 0.99));
    TrustedOcrEngine engine =
        engine(ClaimDocumentTemplateRegistry.builtIn(), new NoOpDocumentClassifier(), provider);
    TrustedOcrDocument doc = parse(engine, OcrDocType.GENERAL, provider);
    assertThat(doc.docType()).isEqualTo(OcrDocType.GENERAL);
    assertThat(doc.fields()).hasSize(1);
  }
}
