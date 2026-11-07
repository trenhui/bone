package com.bone.aidoc.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.OcrDocType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 分类器适配器：未知票种 / 服务故障 / 超限一律回落 GENERAL。 */
class HttpDocumentClassifierTest {

  private static ByteArrayInputStream input(int size) {
    return new ByteArrayInputStream(new byte[size]);
  }

  @Test
  void parses_doc_type_and_strips_trailing_slash() {
    HttpDocumentClassifier c =
        new HttpDocumentClassifier(
            "http://clf:8600/",
            (url, json) -> {
              assertThat(url).isEqualTo("http://clf:8600/classify");
              return "{\"doc_type\":\"invoice\"}";
            });
    assertThat(c.classify(input(8), "ref")).isEqualTo(OcrDocType.INVOICE);
  }

  @Test
  void unknown_doc_type_falls_back_to_general() {
    HttpDocumentClassifier c =
        new HttpDocumentClassifier("http://clf", (url, json) -> "{\"doc_type\":\"UNKNOWN\"}");
    assertThat(c.classify(input(8), "ref")).isEqualTo(OcrDocType.GENERAL);
  }

  @Test
  void transport_failure_and_malformed_json_are_fail_closed() {
    HttpDocumentClassifier down =
        new HttpDocumentClassifier(
            "http://clf",
            (url, json) -> {
              throw new IOException("unreachable");
            });
    assertThat(down.classify(input(8), "ref")).isEqualTo(OcrDocType.GENERAL);
    HttpDocumentClassifier bad = new HttpDocumentClassifier("http://clf", (url, json) -> "<html>");
    assertThat(bad.classify(input(8), "ref")).isEqualTo(OcrDocType.GENERAL);
    assertThat(down.classify(null, "ref")).isEqualTo(OcrDocType.GENERAL);
  }

  @Test
  void oversized_input_is_rejected_before_remote_call() {
    AtomicInteger calls = new AtomicInteger();
    HttpDocumentClassifier c =
        new HttpDocumentClassifier(
            "http://clf",
            (url, json) -> {
              calls.incrementAndGet();
              return "{\"doc_type\":\"INVOICE\"}";
            },
            16L);
    assertThat(c.classify(input(17), "ref")).isEqualTo(OcrDocType.GENERAL);
    assertThat(calls.get()).isZero();
    assertThat(c.classify(input(16), "ref")).isEqualTo(OcrDocType.INVOICE);
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void request_body_is_bounded_base64_json() {
    HttpDocumentClassifier c =
        new HttpDocumentClassifier(
            "http://clf",
            (url, json) -> {
              assertThat(json).contains("image", "source_ref", "cmVm");
              return "{\"doc_type\":\"POLICY\"}";
            });
    assertThat(c.classify(new ByteArrayInputStream("ref".getBytes(StandardCharsets.UTF_8)), "ref"))
        .isEqualTo(OcrDocType.POLICY);
  }
}
