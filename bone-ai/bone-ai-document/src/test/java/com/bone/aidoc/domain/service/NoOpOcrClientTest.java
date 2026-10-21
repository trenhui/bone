package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrResult;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class NoOpOcrClientTest {

  private final NoOpOcrClient client = new NoOpOcrClient();

  @Test
  void returns_empty_result_tagged_noop() {
    OcrResult r = client.recognize(InputStream.nullInputStream(), "x.jpg", OcrDocType.INVOICE);

    assertThat(r.provider()).isEqualTo("noop");
    assertThat(r.docType()).isEqualTo(OcrDocType.INVOICE);
    assertThat(r.fields()).isEmpty();
    assertThat(r.lineItems()).isEmpty();
    assertThat(r.confidence()).isZero();
  }

  @Test
  void provider_is_noop() {
    assertThat(client.provider()).isEqualTo("noop");
  }
}
