package com.bone.aidoc.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.ConfidenceSource;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrFieldEvidence;
import com.bone.aidoc.domain.model.QualityReport;
import com.bone.aidoc.domain.model.ReviewStatus;
import com.bone.aidoc.domain.model.ValidationStatus;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Jev 适配器：正常解析 + 各类故障一律 fail-closed。 */
class JevHttpQualityEvaluatorTest {

  private static OcrFieldEvidence field(String name) {
    return new OcrFieldEvidence(
        name,
        "v",
        null,
        1,
        null,
        "eng",
        0.99,
        ConfidenceSource.OCR_ENGINE,
        ValidationStatus.PASSED,
        ReviewStatus.PENDING);
  }

  private static List<OcrFieldEvidence> fields() {
    return List.of(field("total_amount"), field("invoice_no"));
  }

  @Test
  void parses_field_scores_and_request_contract() {
    AtomicReference<String> body = new AtomicReference<>();
    JevHttpQualityEvaluator ev =
        new JevHttpQualityEvaluator(
            "http://jev:8500/",
            (url, json) -> {
              assertThat(url).isEqualTo("http://jev:8500/score");
              body.set(json);
              return "{\"scores\":{\"total_amount\":0.97,\"invoice_no\":0.88}}";
            });
    QualityReport r = ev.evaluate(fields(), OcrDocType.INVOICE, "ref-1");

    assertThat(ev.evaluatorId()).isEqualTo("jev-http");
    assertThat(r.scoreFor("total_amount")).isEqualTo(0.97);
    assertThat(r.scoreFor("invoice_no")).isEqualTo(0.88);
    assertThat(r.overall()).isEqualTo(0.925);
    assertThat(body.get()).contains("INVOICE", "ref-1", "total_amount");
  }

  @Test
  void out_of_range_or_missing_scores_are_not_trusted() {
    JevHttpQualityEvaluator ev =
        new JevHttpQualityEvaluator(
            "http://jev",
            (url, json) ->
                "{\"scores\":{\"total_amount\":1.5,\"invoice_no\":-0.2,\"unknown\":0.9}}");
    QualityReport r = ev.evaluate(fields(), OcrDocType.INVOICE, "ref");
    assertThat(Double.isNaN(r.scoreFor("total_amount"))).isTrue();
    assertThat(Double.isNaN(r.scoreFor("invoice_no"))).isTrue();
    assertThat(Double.isNaN(r.overall())).isTrue();
  }

  @Test
  void transport_failure_yields_unavailable_quality_not_fake_score() {
    JevHttpQualityEvaluator ev =
        new JevHttpQualityEvaluator(
            "http://jev",
            (url, json) -> {
              throw new IOException("timeout");
            });
    QualityReport r = ev.evaluate(fields(), OcrDocType.INVOICE, "ref");
    assertThat(r.evaluatorId()).isEqualTo("jev-http");
    assertThat(Double.isNaN(r.scoreFor("total_amount"))).isTrue();
  }

  @Test
  void malformed_json_is_fail_closed() {
    JevHttpQualityEvaluator ev =
        new JevHttpQualityEvaluator("http://jev", (url, json) -> "not-json");
    assertThat(
            Double.isNaN(ev.evaluate(fields(), OcrDocType.INVOICE, "r").scoreFor("total_amount")))
        .isTrue();
  }

  @Test
  void empty_fields_skip_remote_call_entirely() {
    AtomicInteger calls = new AtomicInteger();
    JevHttpQualityEvaluator ev =
        new JevHttpQualityEvaluator(
            "http://jev",
            (url, json) -> {
              calls.incrementAndGet();
              return "{}";
            });
    QualityReport r = ev.evaluate(List.of(), OcrDocType.INVOICE, "ref");
    assertThat(calls.get()).isZero();
    assertThat(r.fieldScores()).isEmpty();
  }
}
