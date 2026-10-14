package com.bone.ai.gateway.infrastructure;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的 LLM 客户端（一期自建，零框架依赖）。
 *
 * <p>vLLM / Ollama / DashScope / DeepSeek / OneAPI 均为 OpenAI chat/completions 兼容协议，本实现覆盖该协议全家桶；
 * 一期不引入 Spring AI（官方 1.1.x 已随 Boot 3.5.x 于 2026-06-30 OSS EOL 且带未修复 CVE，2.x 绑 Boot 4）。Jackson
 * 自持，与仓内 {@code RestClientImpl} 一致。
 */
public class OpenAiCompatibleClient implements LlmClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final LlmHttpTransport transport;
  private final String baseUrl;
  private final String apiKey;
  private final String defaultModel;

  public OpenAiCompatibleClient(
      LlmHttpTransport transport, String baseUrl, String apiKey, String defaultModel) {
    this.transport = transport;
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.apiKey = apiKey;
    this.defaultModel = defaultModel;
  }

  @Override
  public LlmResponse chat(LlmRequest request) {
    String url = baseUrl + "/chat/completions";
    String raw = transport.post(url, headers(), buildBody(request));
    return parse(raw, request);
  }

  private Map<String, String> headers() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    if (apiKey != null && !apiKey.isBlank()) {
      headers.put("Authorization", "Bearer " + apiKey);
    }
    return headers;
  }

  private String buildBody(LlmRequest request) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("model", request.model() != null ? request.model() : defaultModel);
      List<Map<String, String>> messages = new ArrayList<>();
      for (LlmMessage m : request.messages()) {
        Map<String, String> mm = new LinkedHashMap<>();
        mm.put("role", m.role());
        mm.put("content", m.content());
        messages.add(mm);
      }
      body.put("messages", messages);
      if (request.temperature() != null) {
        body.put("temperature", request.temperature());
      }
      if (request.maxTokens() != null) {
        body.put("max_tokens", request.maxTokens());
      }
      if (request.jsonResponse()) {
        body.put("response_format", Map.of("type", "json_object"));
      }
      return MAPPER.writeValueAsString(body);
    } catch (Exception e) {
      throw new IllegalStateException("构造 LLM 请求体失败: " + e.getMessage(), e);
    }
  }

  private LlmResponse parse(String raw, LlmRequest request) {
    try {
      JsonNode root = MAPPER.readTree(raw);
      JsonNode choice = root.path("choices").path(0);
      String content = choice.path("message").path("content").asText("");
      String model = root.path("model").asText(request.model());
      String finish = choice.path("finish_reason").asText("stop");
      JsonNode usage = root.path("usage");
      LlmUsage u =
          new LlmUsage(
              usage.path("prompt_tokens").asInt(0),
              usage.path("completion_tokens").asInt(0),
              usage.path("total_tokens").asInt(0));
      return new LlmResponse(content, model, finish, u);
    } catch (Exception e) {
      throw new IllegalStateException("解析 LLM 响应失败: " + e.getMessage(), e);
    }
  }

  private static String trimTrailingSlash(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
