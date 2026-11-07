package com.bone.aidoc.infrastructure.ocr;

import com.bone.aidoc.domain.gateway.DocumentClassifier;
import com.bone.aidoc.domain.model.OcrDocType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 票种分类服务的 HTTP 适配器（ADR-0046 七层管线 ② 的真实实现接入点）。
 *
 * <p><b>fail-closed</b>：任何异常（服务不可达 / 超时 / 非法 JSON / 未知票种 / 文件超限）都返回 {@link
 * OcrDocType#GENERAL}，由调用方显式类型或人工处理，<b>绝不猜测票种</b>——错分票种会连带 错选引擎、错用规则与模板，比不分类更危险。
 *
 * <p>响应契约：{@code {"doc_type": "INVOICE"}}。输入带大小上限，防内存耗尽型 DoS。
 */
public class HttpDocumentClassifier implements DocumentClassifier {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final long DEFAULT_MAX_BYTES = 50L * 1024 * 1024;

  private final String endpoint;
  private final long maxBytes;
  private final HttpOcrClient.OcrTransport transport;

  public HttpDocumentClassifier(String baseUrl, HttpOcrClient.OcrTransport transport) {
    this(baseUrl, transport, DEFAULT_MAX_BYTES);
  }

  public HttpDocumentClassifier(
      String baseUrl, HttpOcrClient.OcrTransport transport, long maxBytes) {
    this.endpoint = trimTrailingSlash(baseUrl) + "/classify";
    this.transport = transport;
    this.maxBytes = maxBytes;
  }

  @Override
  public OcrDocType classify(InputStream input, String sourceRef) {
    if (input == null) {
      return OcrDocType.GENERAL;
    }
    try {
      byte[] bytes = readBounded(input);
      Map<String, Object> req = new LinkedHashMap<>();
      req.put("image", Base64.getEncoder().encodeToString(bytes));
      req.put("source_ref", sourceRef == null ? "" : sourceRef);
      String json = transport.post(endpoint, MAPPER.writeValueAsString(req));
      String name =
          MAPPER.readTree(json).path("doc_type").asText("").trim().toUpperCase(Locale.ROOT);
      return name.isEmpty() ? OcrDocType.GENERAL : OcrDocType.valueOf(name);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return OcrDocType.GENERAL;
    } catch (IOException | RuntimeException e) {
      // valueOf 未知票种抛 IllegalArgumentException（RuntimeException 子类）；一律回落 GENERAL
      return OcrDocType.GENERAL;
    }
  }

  private byte[] readBounded(InputStream input) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    long total = 0;
    int n;
    while ((n = input.read(buf)) != -1) {
      total += n;
      if (total > maxBytes) {
        throw new IOException("分类文件超过大小上限 " + maxBytes + " 字节");
      }
      bos.write(buf, 0, n);
    }
    return bos.toByteArray();
  }

  private static String trimTrailingSlash(String url) {
    String u = url == null ? "" : url.trim();
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    return u;
  }
}
