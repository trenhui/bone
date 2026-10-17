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

  @Test
  void rejects_oversized_upload_without_buffering_it() {
    // 上限 8 字节，喂 64 字节：必须在读超限那一刻就拒（否则大文件先吃满内存）
    DoclingTransport stub =
        (url, filename, body) -> {
          throw new AssertionError("超限文档不应发出请求");
        };
    DoclingSidecarAdapter adapter =
        new DoclingSidecarAdapter("http://sidecar:5001", stub, 8L, 1000, 0, 0);

    assertThatThrownBy(() -> adapter.parse(new ByteArrayInputStream(new byte[64]), "big.pdf"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(DoclingSidecarAdapter.ERROR_TOO_LARGE);
  }

  @Test
  void rejects_too_many_pages() {
    DoclingTransport stub =
        (url, filename, body) -> "{\"page_count\":5,\"pages\":[\"a\",\"b\",\"c\",\"d\",\"e\"]}";
    DoclingSidecarAdapter adapter =
        new DoclingSidecarAdapter("http://sidecar:5001", stub, 1024L, 3, 0, 0);

    assertThatThrownBy(
            () ->
                adapter.parse(
                    new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), "doc.pdf"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(DoclingSidecarAdapter.ERROR_TOO_MANY_PAGES);
  }

  @Test
  void retries_transient_failure_then_succeeds() {
    int[] calls = {0};
    DoclingTransport stub =
        (url, filename, body) -> {
          calls[0]++;
          if (calls[0] < 3) {
            throw new IOException("transient 503");
          }
          return "{\"page_count\":1,\"pages\":[\"p1\"]}";
        };
    DoclingSidecarAdapter adapter =
        new DoclingSidecarAdapter("http://sidecar:5001", stub, 1024L, 1000, 2, 1);

    ParsedDocument pd =
        adapter.parse(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), "doc.pdf");

    assertThat(calls[0]).isEqualTo(3);
    assertThat(pd.pageCount()).isEqualTo(1);
  }

  @Test
  void fails_after_retries_exhausted() {
    int[] calls = {0};
    DoclingTransport stub =
        (url, filename, body) -> {
          calls[0]++;
          throw new IOException("still down");
        };
    DoclingSidecarAdapter adapter =
        new DoclingSidecarAdapter("http://sidecar:5001", stub, 1024L, 1000, 2, 1);

    assertThatThrownBy(
            () ->
                adapter.parse(
                    new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), "doc.pdf"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(DoclingSidecarAdapter.ERROR_SIDECAR_UNAVAILABLE);
    assertThat(calls[0]).isEqualTo(3); // 首次 + 2 次重试
  }

  @Test
  void rejects_null_input_stream() {
    DoclingTransport stub = (url, filename, body) -> "{\"pages\":[]}";
    DoclingSidecarAdapter adapter = new DoclingSidecarAdapter("http://sidecar:5001", stub);

    assertThatThrownBy(() -> adapter.parse(null, "doc.pdf"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(DoclingSidecarAdapter.ERROR_READ_FAILED);
  }
}
