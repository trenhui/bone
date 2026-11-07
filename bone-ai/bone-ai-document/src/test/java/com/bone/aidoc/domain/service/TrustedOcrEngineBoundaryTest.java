package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.aidoc.domain.gateway.FieldValidationRule;
import com.bone.aidoc.domain.gateway.OcrProvider;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.OcrLineItem;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.RecognizedField;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Phase-6 入口边界与可审计性：空流/超限/空识别结果不得静默产出「无需人工」的可信文档。 */
class TrustedOcrEngineBoundaryTest {

  private static final byte[] IMAGE = "fake-image-bytes".getBytes(StandardCharsets.UTF_8);

  private static final class StubProvider implements OcrProvider {
    private final OcrResult result;

    StubProvider(OcrResult result) {
      this.result = result;
    }

    @Override
    public String engineId() {
      return "stub";
    }

    @Override
    public boolean supports(OcrDocType docType) {
      return true;
    }

    @Override
    public OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType) {
      return result;
    }
  }

  /** 记录实际被读取的字节数，用于证明「不是先全量读入内存再判断上限」。 */
  private static final class CountingStream extends InputStream {
    private final int total;
    private int delivered;
    long bytesRead;

    CountingStream(int total) {
      this.total = total;
    }

    @Override
    public int read() {
      if (delivered >= total) {
        return -1;
      }
      delivered++;
      bytesRead++;
      return 1;
    }

    @Override
    public int read(byte[] b, int off, int len) {
      if (delivered >= total) {
        return -1;
      }
      int n = Math.min(len, total - delivered);
      Arrays.fill(b, off, off + n, (byte) 1);
      delivered += n;
      bytesRead += n;
      return n;
    }
  }

  private static OcrResult normalResult() {
    return new OcrResult(
        "stub",
        OcrDocType.INVOICE,
        List.of(new RecognizedField("hospital_name", "某医院", 0.99)),
        List.of(),
        "raw",
        1,
        0.99);
  }

  private static TrustedOcrEngine engine(OcrResult result, long maxBytes) {
    return engine(result, maxBytes, List.of(), ClaimDocumentTemplateRegistry.noTemplates());
  }

  private static TrustedOcrEngine engine(
      OcrResult result,
      long maxBytes,
      List<FieldValidationRule> rules,
      ClaimDocumentTemplateRegistry templates) {
    return new TrustedOcrEngine(
        List.of(new StubProvider(result)),
        new NoOpOcrQualityEvaluator(),
        new DefaultReviewPolicy(),
        new ClaimFieldRisk(templates),
        rules,
        null,
        templates,
        null,
        maxBytes);
  }

  private static TrustedOcrDocument parse(TrustedOcrEngine e, InputStream in) {
    return e.parse(in, "t1", "src.jpg", "d1", OcrDocType.INVOICE);
  }

  @Test
  void null_input_is_rejected() {
    assertThatThrownBy(() -> parse(engine(normalResult(), Long.MAX_VALUE), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("输入流为空");
  }

  @Test
  void empty_input_stream_is_rejected_not_silently_accepted() {
    assertThatThrownBy(
            () -> parse(engine(normalResult(), Long.MAX_VALUE), InputStream.nullInputStream()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("空字节流", "假绿");
  }

  @Test
  @Timeout(10)
  void oversized_input_is_rejected_before_reading_the_whole_stream() {
    CountingStream stream = new CountingStream(1_000_000);
    assertThatThrownBy(() -> parse(engine(normalResult(), 100), stream))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("超过大小上限", "100");
    // 有界读取：读到第一个缓冲块即停止，绝不把 1MB 全量吃进内存
    assertThat(stream.bytesRead).isLessThan(1_000_000L);
  }

  @Test
  void primary_engine_empty_result_is_rejected() {
    OcrResult empty = OcrResult.empty("stub", OcrDocType.INVOICE);
    assertThatThrownBy(() -> parse(engine(empty, Long.MAX_VALUE), new ByteArrayInputStream(IMAGE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("空证据链", "转人工复核");
  }

  @Test
  void primary_engine_null_fields_is_rejected() {
    OcrResult nullFields = new OcrResult("stub", OcrDocType.INVOICE, null, List.of(), "", 1, 0.9);
    assertThatThrownBy(
            () -> parse(engine(nullFields, Long.MAX_VALUE), new ByteArrayInputStream(IMAGE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("空证据链");
  }

  @Test
  void every_field_gets_a_decision_reason() {
    TrustedOcrDocument doc =
        parse(engine(normalResult(), Long.MAX_VALUE), new ByteArrayInputStream(IMAGE));
    assertThat(doc.decisionReasons()).containsKey("hospital_name");
    assertThat(doc.decisionReasons().get("hospital_name")).contains("[放行]");
  }

  @Test
  void l2_failure_reason_and_rule_id_are_preserved() {
    OcrResult bad =
        new OcrResult(
            "stub",
            OcrDocType.INVOICE,
            List.of(new RecognizedField("total_amount", "12x", 0.99)),
            List.of(),
            "raw",
            1,
            0.99);
    TrustedOcrDocument doc =
        parse(
            engine(
                bad,
                Long.MAX_VALUE,
                List.of(new NumericAmountRule()),
                ClaimDocumentTemplateRegistry.noTemplates()),
            new ByteArrayInputStream(IMAGE));
    assertThat(doc.decisionReasons().get("total_amount"))
        .contains("[amount-numeric-format]", "金额格式非法");
    assertThat(doc.fields().get(0).reviewStatus()).isEqualTo(ReviewStatus.MANUAL_REVIEW);
  }

  @Test
  void l4_mismatch_reaches_the_final_document_as_failed_and_manual() {
    OcrResult mismatch =
        new OcrResult(
            "stub",
            OcrDocType.INVOICE,
            List.of(
                new RecognizedField("total_amount", "200.00", 0.999),
                new RecognizedField("invoice_no", "FP1", 0.99),
                new RecognizedField("invoice_date", "2026-01-01", 0.99)),
            List.of(new OcrLineItem("检查费", 30.0), new OcrLineItem("药费", 70.0)),
            "raw",
            1,
            0.99);
    TrustedOcrDocument doc =
        parse(
            engine(mismatch, Long.MAX_VALUE, List.of(), ClaimDocumentTemplateRegistry.builtIn()),
            new ByteArrayInputStream(IMAGE));
    OcrFieldEvidence total =
        doc.fields().stream()
            .filter(f -> "total_amount".equals(f.fieldName()))
            .findFirst()
            .orElseThrow();
    assertThat(total.validationStatus()).isEqualTo(ValidationStatus.FAILED);
    assertThat(total.reviewStatus()).isEqualTo(ReviewStatus.MANUAL_REVIEW);
    assertThat(doc.decisionReasons().get("total_amount")).contains("[L4]", "不一致");
  }
}
