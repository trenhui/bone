package com.bone.airag.infrastructure.rerank;

import com.bone.airag.common.AiRagErrorCodes;
import com.bone.airag.domain.gateway.RerankPort;
import com.bone.airag.domain.model.RerankCandidate;
import com.bone.airag.domain.model.RerankScored;
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
 * 共享重排序服务客户端（cross-encoder / bge-reranker，TEI / infinity 兼容）。
 *
 * <p>把 query 与候选文本批量 POST 到 {@code {baseUrl}/rerank}，取回每对的 relevance 分后精排。 cross-encoder 在
 * query-document 对上直接打分，比 RRF 粗融合精度高，是「引用正确率」达标的关键（详设 §2.6 top-30 → top-5）。
 *
 * <p>provider 故障抛 {@link #RERANK_PROVIDER_FAIL}；上层检索链 catch 后<b>降级为 NoOp 序（RRF 粗序）并 WARN</b>——读路径可用性优先，且降级可观测（WARN + 错误码），不是「假装已精排」；引用强制语义在降级下不变（2026-10-23 口径订正：原「不静默降级」表述与检索链降级决策不一致，以本处为准）。
 */
public class HttpBgeRerankClient implements RerankPort {

  private static final Logger log = LoggerFactory.getLogger(HttpBgeRerankClient.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String endpoint;
  private final String model;
  private final Duration timeout;
  private final HttpClient http;

  public HttpBgeRerankClient(String baseUrl, String model, Duration timeout) {
    this.endpoint = trimTrailingSlash(baseUrl) + "/rerank";
    this.model = model;
    this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Override
  public List<RerankScored> rerank(String query, List<RerankCandidate> candidates, int topK) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    List<String> texts = new ArrayList<>(candidates.size());
    for (RerankCandidate c : candidates) {
      texts.add(c.text() == null ? "" : c.text());
    }
    String body;
    try {
      Map<String, Object> req = new LinkedHashMap<>();
      req.put("query", query == null ? "" : query);
      req.put("texts", texts);
      req.put("model", model);
      body = MAPPER.writeValueAsString(req);
    } catch (IOException e) {
      throw new BizException(500, "重排序请求序列化失败", AiRagErrorCodes.RERANK_PROVIDER_FAIL);
    }
    double[] scores = parseScores(post(body), candidates.size());
    return rank(candidates, scores, topK);
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
            502, "重排序服务响应异常 status=" + response.statusCode(), AiRagErrorCodes.RERANK_PROVIDER_FAIL);
      }
      return response.body();
    } catch (BizException e) {
      throw e;
    } catch (Exception e) {
      log.warn("重排序服务调用失败 endpoint={}", endpoint, e);
      throw new BizException(
          502, "重排序服务调用失败: " + e.getMessage(), AiRagErrorCodes.RERANK_PROVIDER_FAIL);
    }
  }

  /**
   * 解析 TEI /rerank 响应 {@code [{"index":0,"score":0.9},...]} → 按 candidates 顺序对齐的分数数组。
   *
   * <p>缺失 / 越界 index 的项补 0（该候选未被打分时按最低分处理，不抛错中断整批）。
   */
  static double[] parseScores(String json, int size) {
    double[] scores = new double[size];
    try {
      JsonNode root = MAPPER.readTree(json);
      if (!root.isArray()) {
        throw new BizException(502, "重排序响应结构非法（非数组）", AiRagErrorCodes.RERANK_PROVIDER_FAIL);
      }
      for (JsonNode n : root) {
        int idx = n.path("index").asInt(-1);
        double score = n.path("score").asDouble(0.0);
        if (idx >= 0 && idx < size) {
          scores[idx] = score;
        }
      }
    } catch (BizException e) {
      throw e;
    } catch (IOException e) {
      throw new BizException(502, "重排序响应解析失败", AiRagErrorCodes.RERANK_PROVIDER_FAIL);
    }
    return scores;
  }

  /** 按分数降序精排，取前 {@code topK}；分数相同保持输入先后（稳定排序）。 */
  static List<RerankScored> rank(List<RerankCandidate> candidates, double[] scores, int topK) {
    int k = Math.max(0, Math.min(topK, candidates.size()));
    List<Integer> order = new ArrayList<>(candidates.size());
    for (int i = 0; i < candidates.size(); i++) {
      order.add(i);
    }
    order.sort(
        (a, b) -> {
          int c = Double.compare(scores[b], scores[a]);
          return c != 0 ? c : Integer.compare(a, b);
        });
    List<RerankScored> out = new ArrayList<>(k);
    for (int i = 0; i < k; i++) {
      int j = order.get(i);
      out.add(new RerankScored(candidates.get(j).ref(), scores[j]));
    }
    return out;
  }

  private static String trimTrailingSlash(String url) {
    String u = url == null ? "" : url.trim();
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    return u;
  }
}
