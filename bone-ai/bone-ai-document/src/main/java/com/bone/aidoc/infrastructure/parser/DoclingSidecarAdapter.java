package com.bone.aidoc.infrastructure.parser;

import com.bone.aidoc.common.AiDocErrorCodes;
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
import java.util.UUID;

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
 *
 * <p><b>资源边界防护（2026-10-17 复核修复，详设 §2.5 的 50MB/1000 页约束此前只有 javadoc）</b>：
 *
 * <ul>
 *   <li>上传大小上限：边读边计数，超限立即拒绝（不再全量 {@code readAllBytes()} 进内存，防内存耗尽型 DoS）；
 *   <li>页数上限：sidecar 返回页数超限即拒（防超大文档拖垮下游切片与判定）；
 *   <li>失败重试：瞬时故障（IOException / 5xx）指数退避重试，上限可配；
 *   <li>错误字典：每个失败分支带稳定码（见类常量），application 层据此映射 BizException。
 * </ul>
 */
public class DoclingSidecarAdapter implements DocumentParser {

  // ---- 稳定错误码 ----
  /**
   * 真源已收敛至 {@link AiDocErrorCodes}（2026-10-18）：i18n 一致性门禁只扫 {@code *ErrorCodes.java}，
   * 码散落在适配器里时台账与代码字符串的一致性从未被校验（假安全）。此处保留同名字段作委托，调用点零改动； 新引用请直接用 {@code AiDocErrorCodes.*}。
   */
  /** 文档超过大小上限（拒收，防内存耗尽）。 */
  @Deprecated public static final String ERROR_TOO_LARGE = AiDocErrorCodes.TOO_LARGE;

  /** 页数超过上限（拒收，防下游失控）。 */
  @Deprecated public static final String ERROR_TOO_MANY_PAGES = AiDocErrorCodes.TOO_MANY_PAGES;

  /** sidecar 不可用 / 重试耗尽 / 非 2xx / 响应契约不符。 */
  @Deprecated
  public static final String ERROR_SIDECAR_UNAVAILABLE = AiDocErrorCodes.SIDECAR_UNAVAILABLE;

  /** 输入流读取失败或为空。 */
  @Deprecated public static final String ERROR_READ_FAILED = AiDocErrorCodes.READ_FAILED;

  /** 默认上传大小上限 50MB（详设 §2.5）。 */
  public static final long DEFAULT_MAX_BYTES = 50L * 1024 * 1024;

  /** 默认页数上限 1000（详设 §2.5）。 */
  public static final int DEFAULT_MAX_PAGES = 1000;

  /** 默认重试次数（首次 + 2 次重试，详设「重试 ≤2 次」）。 */
  public static final int DEFAULT_MAX_RETRIES = 2;

  private static final long DEFAULT_BACKOFF_MS = 100;

  private final String baseUrl;
  private final DoclingTransport transport;
  private final long maxBytes;
  private final int maxPages;
  private final int maxRetries;
  private final long backoffMs;
  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public DoclingSidecarAdapter(String baseUrl, long timeoutMs) {
    this(baseUrl, new HttpClientTransport(timeoutMs));
  }

  public DoclingSidecarAdapter(String baseUrl, DoclingTransport transport) {
    this(
        baseUrl,
        transport,
        DEFAULT_MAX_BYTES,
        DEFAULT_MAX_PAGES,
        DEFAULT_MAX_RETRIES,
        DEFAULT_BACKOFF_MS);
  }

  public DoclingSidecarAdapter(
      String baseUrl,
      DoclingTransport transport,
      long maxBytes,
      int maxPages,
      int maxRetries,
      long backoffMs) {
    this.baseUrl = baseUrl;
    this.transport = transport;
    this.maxBytes = maxBytes;
    this.maxPages = maxPages;
    this.maxRetries = maxRetries;
    this.backoffMs = backoffMs;
  }

  @Override
  public ParsedDocument parse(InputStream input, String sourceRef) {
    byte[] bytes = readBounded(input, sourceRef);
    String filename = (sourceRef == null || sourceRef.isBlank()) ? "document" : sourceRef;
    String json = postWithRetry(filename, bytes, sourceRef);
    DoclingResponse resp;
    try {
      resp = objectMapper.readValue(json, DoclingResponse.class);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Docling sidecar 响应契约不符 [" + ERROR_SIDECAR_UNAVAILABLE + "]: " + e.getMessage(), e);
    }
    List<String> pages = resp.pages() == null ? List.of() : resp.pages();
    int pageCount = resp.pageCount() > 0 ? resp.pageCount() : pages.size();
    if (pageCount > maxPages || pages.size() > maxPages) {
      throw new IllegalStateException(
          "文档页数超上限 "
              + maxPages
              + "（pageCount="
              + pageCount
              + ", pages="
              + pages.size()
              + "）["
              + ERROR_TOO_MANY_PAGES
              + "]");
    }
    return ParsedDocument.of(pageCount, pages, "docling");
  }

  @Override
  public String provider() {
    return "docling";
  }

  /**
   * 带上限的流读取：边读边计数，超限立即拒绝。
   *
   * <p>不先 {@code readAllBytes()}——那会让一个超大上传先把 JVM 内存吃光才谈上限检查。
   */
  private byte[] readBounded(InputStream input, String sourceRef) {
    if (input == null) {
      throw new IllegalStateException("文档输入流为空 [" + ERROR_READ_FAILED + "]");
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    long total = 0;
    try {
      int n;
      while ((n = input.read(buf)) != -1) {
        total += n;
        if (total > maxBytes) {
          throw new IllegalStateException(
              "文档超过大小上限 "
                  + maxBytes
                  + " 字节（已读 "
                  + total
                  + "）["
                  + ERROR_TOO_LARGE
                  + "]: "
                  + sourceRef);
        }
        bos.write(buf, 0, n);
      }
    } catch (IOException e) {
      throw new IllegalStateException("读取文档失败 [" + ERROR_READ_FAILED + "]: " + sourceRef, e);
    }
    return bos.toByteArray();
  }

  /** 调用 sidecar，瞬时故障指数退避重试；InterruptedException 立即失败并恢复中断标记（不重试）。 */
  private String postWithRetry(String filename, byte[] bytes, String sourceRef) {
    IOException last = null;
    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return transport.post(baseUrl + "/v1/convert/source", filename, bytes);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Docling sidecar 调用被中断 [" + ERROR_SIDECAR_UNAVAILABLE + "]: " + sourceRef, e);
      } catch (IOException e) {
        last = e;
        if (attempt < maxRetries && !sleepBackoff(attempt)) {
          throw new IllegalStateException(
              "Docling sidecar 重试等待被中断 [" + ERROR_SIDECAR_UNAVAILABLE + "]: " + sourceRef, e);
        }
      }
    }
    throw new IllegalStateException(
        "Docling sidecar 调用失败(重试 "
            + maxRetries
            + " 次耗尽) ["
            + ERROR_SIDECAR_UNAVAILABLE
            + "]: "
            + (last == null ? "unknown" : last.getMessage()),
        last);
  }

  private boolean sleepBackoff(int attempt) {
    try {
      Thread.sleep(backoffMs * (1L << attempt));
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** sidecar 传输契约（可注入：默认 HttpClient 实现，测试用桩）。 */
  public interface DoclingTransport {
    String post(String url, String filename, byte[] body) throws IOException, InterruptedException;
  }

  /** 默认传输：JDK HttpClient 发 multipart/form-data（对齐 Docling /v1/convert/source）。 */
  public static final class HttpClientTransport implements DoclingTransport {
    private final HttpClient client;
    private final long timeoutMs;

    public HttpClientTransport(long timeoutMs) {
      this.timeoutMs = timeoutMs;
      this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    }

    @Override
    public String post(String url, String filename, byte[] body)
        throws IOException, InterruptedException {
      String boundary = "----BoneDocling" + UUID.randomUUID().toString().replace("-", "");
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
              .timeout(Duration.ofMillis(timeoutMs))
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
