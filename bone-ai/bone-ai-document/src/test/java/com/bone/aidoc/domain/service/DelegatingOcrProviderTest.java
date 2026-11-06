package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.gateway.OcrPort;
import com.bone.aidoc.domain.gateway.OcrProvider;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrResult;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link DelegatingOcrProvider} 桥接既有 {@link OcrPort} 的断言。 */
class DelegatingOcrProviderTest {

  private static final class StubOcrPort implements OcrPort {
    OcrResult last;

    @Override
    public OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType) {
      last = OcrResult.empty("stub", docType);
      return last;
    }

    @Override
    public String provider() {
      return "stub";
    }
  }

  @Test
  void delegates_recognize_and_exposes_metadata() {
    StubOcrPort port = new StubOcrPort();
    OcrProvider provider =
        new DelegatingOcrProvider(port, "paddle-ocr", Set.of(OcrDocType.INVOICE));

    assertThat(provider.engineId()).isEqualTo("paddle-ocr");
    assertThat(provider.supports(OcrDocType.INVOICE)).isTrue();
    assertThat(provider.supports(OcrDocType.ID_CARD)).isFalse();

    provider.recognize(InputStream.nullInputStream(), "inv.jpg", OcrDocType.INVOICE);
    assertThat(port.last).isNotNull();
    assertThat(port.last.docType()).isEqualTo(OcrDocType.INVOICE);
  }

  @Test
  void empty_supported_set_means_universal() {
    OcrProvider provider = new DelegatingOcrProvider(new StubOcrPort(), "x", Set.of());
    assertThat(provider.supports(OcrDocType.POLICY)).isTrue();
    assertThat(provider.supports(OcrDocType.ID_CARD)).isTrue();
  }
}
