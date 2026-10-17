package com.bone.aidoc.infrastructure.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.aidoc.infrastructure.parser.DoclingSidecarAdapter.DoclingTransport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DoclingSidecarAdapterTest {

  @Test
  void maps_sidecar_json_to_pages() {
    DoclingTransport stub = (url, filename, body) -> "{\"page_count\":2,\"pages\":[\"p1\",\"p2\"]}";
    DoclingSidecarAdapter adapter = new DoclingSidecarAdapter("http://sidecar:5001", stub);

    ParsedDocument pd =
        adapter.parse(
            new ByteArrayInputStream("dummy".getBytes(StandardCharsets.UTF_8)), "doc.pdf");

    assertThat(pd.provider()).isEqualTo("docling");
    assertThat(pd.pageCount()).isEqualTo(2);
    assertThat(pd.pageTexts()).containsExactly("p1", "p2");
  }

  @Test
  void derives_page_count_from_pages_when_absent() {
    DoclingTransport stub = (url, filename, body) -> "{\"pages\":[\"a\",\"b\",\"c\"]}";
    DoclingSidecarAdapter adapter = new DoclingSidecarAdapter("http://sidecar:5001", stub);

    ParsedDocument pd =
        adapter.parse(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), "doc.pdf");

    assertThat(pd.pageCount()).isEqualTo(3);
    assertThat(pd.pageTexts()).containsExactly("a", "b", "c");
  }

  @Test
  void throws_when_sidecar_errors() {
    DoclingTransport stub =
        (url, filename, body) -> {
          throw new IOException("boom");
        };
    DoclingSidecarAdapter adapter = new DoclingSidecarAdapter("http://sidecar:5001", stub);

    assertThatThrownBy(
            () ->
                adapter.parse(
                    new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), "doc.pdf"))
        .isInstanceOf(IllegalStateException.class);
  }
}
