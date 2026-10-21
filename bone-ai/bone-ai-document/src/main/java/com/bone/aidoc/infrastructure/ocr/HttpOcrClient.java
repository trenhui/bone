package com.bone.aidoc.infrastructure.ocr;

import com.bone.aidoc.common.AiDocErrorCodes;
import com.bone.aidoc.domain.gateway.OcrPort;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrLineItem;
import com.bone.aidoc.domain.model.OcrResult;
import com.bone.aidoc.domain.model.RecognizedField;
import com.bone.core.exception.BizException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享 OCR / 视觉识别客户端（TPA 票据 / 病历识别，TEI / 通用 vision 服务兼容）。
 *
 * <p>把图像 base64 后 POST 到 {@code {baseUrl}/ocr}，按 {@code docType} 驱动识别 schema，取回结构化字段 + 费用明细。 与
 * {@link com.bone.aidoc.infrastructure.parser.DoclingSidecarAdapter} 同模式：仅 JDK HttpClient +
 * jackson（零新 Maven 依赖）， 传输层可注入以便单测，资源上限 + 编码化错误。
 *
 * <p>provider 故障（超时 / 非 2xx / 响应契约不符）抛 {@link AiDocErrorCodes#OCR_PROVIDER_FAIL}，不静默返回空——
 * 静默空结果会把「服务挂了」伪装成「图上没字」，污染理算。
 */
public class HttpOcrClient implements OcrPort {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final String endpoint;
  private final String model;
  private final long maxBytes;
  private final OcrTransport transport;

  public HttpOcrClient(String baseUrl, String model, long maxBytes, OcrTransport transport) {
    this.endpoint = trimTrailingSlash(baseUrl) + "/ocr";
    this.model = model;
    this.maxBytes = maxBytes;
    this.transport = transport;
  }

  @Override
  public OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType) {
    byte[] bytes = readBounded(input, sourceRef);
    String body = buildRequestBody(bytes, docType);
    String json;
    try {
      json = transport.post(endpoint, body);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BizException(502, "OCR 调用被中断: " + sourceRef, AiDocErrorCodes.OCR_PROVIDER_FAIL);
    } catch (IOException e) {
      throw new BizException(
          502, "OCR 服务调用失败: " + e.getMessage(), AiDocErrorCodes.OCR_PROVIDER_FAIL);
    }
    return parseResult(json, docType);
  }

  @Override
  public String provider() {
    return "http-ocr";
  }

  /** 构造识别请求体：base64 图像 + 文档类型 + 模型标识。 */
  String buildRequestBody(byte[] bytes, OcrDocType docType) {
    try {
      Map<String, Object> req = new LinkedHashMap<>();
      req.put("image", Base64.getEncoder().encodeToString(bytes));
      req.put("doc_type", docType.name());
      req.put("model", model);
      return MAPPER.writeValueAsString(req);
    } catch (IOException e) {
      throw new BizException(500, "OCR 请求序列化失败", AiDocErrorCodes.OCR_PROVIDER_FAIL);
    }
  }

  /**
   * 解析识别服务响应为 {@link OcrResult}：
   *
   * <pre>
   * { "fields":[{"name","value","confidence"}], "line_items":[{"item_name","amount"}],
   *   "text":"...", "page_count":1, "confidence":0.95 }
   * </pre>
   *
   * <p>缺失 / 非数组节点按空处理（宽容契约），结构整体非法才抛错。
   */
  static OcrResult parseResult(String json, OcrDocType docType) {
    try {
      JsonNode root = MAPPER.readTree(json);
      List<RecognizedField> fields = new ArrayList<>();
      for (JsonNode f : root.path("fields")) {
        fields.add(
            new RecognizedField(
                f.path("name").asText(""),
                f.path("value").asText(""),
                f.path("confidence").asDouble(0.0)));
      }
      List<OcrLineItem> items = new ArrayList<>();
      for (JsonNode it : root.path("line_items")) {
        items.add(
            new OcrLineItem(it.path("item_name").asText(""), it.path("amount").asDouble(0.0)));
      }
      return new OcrResult(
          "http-ocr",
          docType,
          fields,
          items,
          root.path("text").asText(""),
          root.path("page_count").asInt(0),
          root.path("confidence").asDouble(0.0));
    } catch (IOException e) {
      throw new BizException(502, "OCR 响应解析失败", AiDocErrorCodes.OCR_PROVIDER_FAIL);
    }
  }

  /** 带上限的流读取（边读边计数，超限即拒，防内存耗尽型 DoS——同 Docling 策略）。 */
  private byte[] readBounded(InputStream input, String sourceRef) {
    if (input == null) {
      throw new BizException(400, "识别输入流为空: " + sourceRef, AiDocErrorCodes.READ_FAILED);
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    long total = 0;
    try {
      int n;
      while ((n = input.read(buf)) != -1) {
        total += n;
        if (total > maxBytes) {
          throw new BizException(
              413,
              "识别文件超过大小上限 " + maxBytes + " 字节（已读 " + total + "）: " + sourceRef,
              AiDocErrorCodes.TOO_LARGE);
        }
        bos.write(buf, 0, n);
      }
    } catch (IOException e) {
      throw new BizException(400, "读取识别文件失败: " + sourceRef, AiDocErrorCodes.READ_FAILED);
    }
    return bos.toByteArray();
  }

  /** 识别服务传输契约（可注入：默认 HttpClient 实现，测试用桩）。 */
  public interface OcrTransport {
    String post(String url, String jsonBody) throws IOException, InterruptedException;
  }

  /** 默认传输：JDK HttpClient POST JSON。 */
  public static final class HttpClientTransport implements OcrTransport {
    private final HttpClient client;
    private final long timeoutMs;

    public HttpClientTransport(long timeoutMs) {
      this.timeoutMs = timeoutMs;
      this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    }

    @Override
    public String post(String url, String jsonBody) throws IOException, InterruptedException {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofMillis(timeoutMs))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new IOException("OCR service http " + response.statusCode());
      }
      return response.body();
    }
  }

  private static String trimTrailingSlash(String url) {
    String u = url == null ? "" : url.trim();
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    return u;
  }
}
