package com.bone.airag.infrastructure.embedding;

import com.bone.airag.common.AiRagErrorCodes;
import com.bone.airag.domain.gateway.EmbeddingPort;
import com.bone.core.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 共享嵌入服务客户端（TEI / infinity 兼容）：把文本 POST 到 {@code {baseUrl}/embed}，取回语义向量。
 *
 * <p>bge-m3（1024 维）推理放在共享服务而非每副本进程内——多副本友好、避免每 jar 加载 GB 级 ONNX 模型、 可按需水平扩容 GPU（详设 §2.5/§2.6
 * 演进项）。进程内 ONNX 作 L4 备选，经 {@link EmbeddingPort} 端口由消费方覆盖。
 *
 * <p>provider 故障（超时 / 非 2xx / 响应非法）抛 {@link #EMBEDDING_PROVIDER_FAIL}，不静默返回零向量——
 * 静默零向量会把「服务挂了」伪装成「低相似度」，污染检索排序。检索链 catch 该异常后<b>降级为 BM25 单路并 WARN</b>（读路径可用性优先；严禁切云端辅路）。
 */
public class HttpBgeEmbeddingClient implements EmbeddingPort {

  private static final Logger log = LoggerFactory.getLogger(HttpBgeEmbeddingClient.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String endpoint;
  private final String model;
  private final String version;
  private final Duration timeout;
  private final HttpClient http;

  public HttpBgeEmbeddingClient(String baseUrl, String model, String version, Duration timeout) {
    this.endpoint = trimTrailingSlash(baseUrl) + "/embed";
    this.model = model;
    this.version = version;
    this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Override
  public String version() {
    return version;
  }

  @Override
  public float[] embed(String text) {
    String body;
    try {
      Map<String, Object> req = new LinkedHashMap<>();
      req.put("inputs", text == null ? "" : text);
      req.put("model", model);
      body = MAPPER.writeValueAsString(req);
    } catch (IOException e) {
      throw new BizException(500, "嵌入请求序列化失败", AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
    }
    return parseFirstEmbedding(post(body));
  }

  /**
   * 批量嵌入（TEI / infinity 兼容）：{@code inputs} 传数组，响应为 {@code [[...],[...],...]}。
   *
   * <p>相比逐条 {@code embed}，一次往返拿回全部向量，大语料 ingest 吞吐显著更高。批返回数量必须与输入数量一致， 否则调用方（{@code
   * RagApplicationService.ingest}）fail-closed 抛错。
   */
  @Override
  public List<float[]> embedBatch(List<String> texts) {
    String body;
    try {
      Map<String, Object> req = new LinkedHashMap<>();
      req.put("inputs", texts == null ? List.of("") : texts);
      req.put("model", model);
      body = MAPPER.writeValueAsString(req);
    } catch (IOException e) {
      throw new BizException(500, "嵌入批量请求序列化失败", AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
    }
    return parseEmbeddings(post(body));
  }

  private String post(String body) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(endpoint))
              .timeout(timeout)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new BizException(
            502,
            "嵌入服务响应异常 status=" + response.statusCode(),
            AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
      }
      return response.body();
    } catch (BizException e) {
      throw e;
    } catch (Exception e) {
      log.warn("嵌入服务调用失败 endpoint={}", endpoint, e);
      throw new BizException(
          502, "嵌入服务调用失败: " + e.getMessage(), AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
    }
  }

  /** 解析 {@code [[...floats...]]} 响应，取第一个向量；结构非法抛 {@link #EMBEDDING_PROVIDER_FAIL}。 */
  static float[] parseFirstEmbedding(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      JsonNode first = root.get(0);
      if (first == null || !first.isArray()) {
        throw new BizException(502, "嵌入响应结构非法（缺少向量数组）", AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
      }
      float[] v = new float[first.size()];
      for (int i = 0; i < first.size(); i++) {
        v[i] = (float) first.get(i).asDouble();
      }
      return v;
    } catch (BizException e) {
      throw e;
    } catch (IOException e) {
      throw new BizException(502, "嵌入响应解析失败", AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
    }
  }

  /** 解析 {@code [[...],[...],...]} 批量响应为向量列表；结构非法或数量不符抛 {@link #EMBEDDING_PROVIDER_FAIL}。 */
  public static List<float[]> parseEmbeddings(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      if (!root.isArray()) {
        throw new BizException(502, "嵌入批量响应结构非法（缺少外层数组）", AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
      }
      List<float[]> out = new ArrayList<>(root.size());
      for (JsonNode node : root) {
        if (!node.isArray()) {
          throw new BizException(
              502, "嵌入批量响应结构非法（内层非向量数组）", AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
        }
        float[] v = new float[node.size()];
        for (int i = 0; i < node.size(); i++) {
          v[i] = (float) node.get(i).asDouble();
        }
        out.add(v);
      }
      return out;
    } catch (BizException e) {
      throw e;
    } catch (IOException e) {
      throw new BizException(502, "嵌入批量响应解析失败", AiRagErrorCodes.EMBEDDING_PROVIDER_FAIL);
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
