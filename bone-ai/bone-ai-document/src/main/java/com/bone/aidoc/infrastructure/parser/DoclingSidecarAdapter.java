package com.bone.aidoc.infrastructure.parser;

import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Docling sidecar 解析器（详设 §2.5 / 总案 §4.4「默认 Docling」；第二批 sidecar 接入）。
 *
 * <p>经 HTTP 调 Docling sidecar（地址可配置，部署形态运行时决定），契约对齐 Docling 官方：
 *
 * <pre>
 * POST {baseUrl}/v1/convert/source   (multipart/form-data, files=文档)
 * → 200 { "page_count": N, "pages": ["页1文本", ...] }
 * </pre>
 *
 * <p>仅依赖 JDK HttpClient + jackson（零新 Maven 依赖）；传输层可注入以便单测。 超时 / 非 2xx / 解析失败抛出，交由编排层按 SLA 转
 * HITL——本适配器不伪造 partial 降级语义。
 */
public class DoclingSidecarAdapter implements DocumentParser {

  private final String baseUrl;
  private final DoclingTransport transport;
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public DoclingSidecarAdapter(String baseUrl, long timeoutMs) {
    this(baseUrl, new HttpClientTransport(timeoutMs));
  }

  public DoclingSidecarAdapter(String baseUrl, DoclingTransport transport) {
    this.baseUrl = baseUrl;
    this.transport = transport;
  }

  @Override
  public ParsedDocument parse(InputStream input, String sourceRef) {
    byte[] bytes;
    try {
      bytes = input.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("read document failed: " + sourceRef, e);
    }
    String filename = (sourceRef == null || sourceRef.isBlank()) ? "document" : sourceRef;
    try {
      String json = transport.post(baseUrl + "/v1/convert/source", filename, bytes);
      DoclingResponse resp = objectMapper.readValue(json, DoclingResponse.class);
      List<String> pages = resp.pages() == null ? List.of() : resp.pages();
      int pageCount = resp.pageCount() > 0 ? resp.pageCount() : pages.size();
      return ParsedDocument.of(pageCount, pages, "docling");
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Docling sidecar call failed: " + sourceRef, e);
    }
  }

  @Override
  public String provider() {
    return "docling";
  }

  /** sidecar 传输契约（可注入：默认 HttpClient 实现，测试用桩）。 */
  interface DoclingTransport {
    String post(String url, String filename, byte[] body) throws IOException, InterruptedException;
  }

  /** 默认传输：JDK HttpClient 发 multipart/form-data（对齐 Docling /v1/convert/source）。 */
  static final class HttpClientTransport implements DoclingTransport {
    private final HttpClient client;

    HttpClientTransport(long timeoutMs) {
      this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    }

    @Override
    public String post(String url, String filename, byte[] body)
        throws IOException, InterruptedException {
      String boundary = "----BoneDoclingBoundary";
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
      bos.write(
          ("Content-Disposition: form-data; name=\"files\"; filename=\"" + filename + "\"\r\n")
              .getBytes(StandardCharsets.ISO_8859_1));
      bos.write(
          "Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
      bos.write(body);
      bos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("Content-Type", "multipart/form-data; boundary=" + boundary)
              .POST(HttpRequest.BodyPublishers.ofByteArray(bos.toByteArray()))
              .timeout(Duration.ofSeconds(30))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new IOException("Docling sidecar http " + response.statusCode());
      }
      return response.body();
    }
  }

  private record DoclingResponse(
      @JsonProperty("page_count") int pageCount, @JsonProperty("pages") List<String> pages) {}
}
