package com.bone.ai.gateway.infrastructure;

import java.util.Map;

/**
 * LLM 出站 HTTP 传输 seam：把「发请求」与「构造 / 解析」解耦。
 *
 * <p>用途：① 单测用假传输覆盖真实链路（不连网）；② 为后续熔断 / 重试 / 计量留出替换位，与仓内 {@code RestClientImpl} 的 Resilience4j
 * 熔断思路一致。
 */
@FunctionalInterface
public interface LlmHttpTransport {

  /**
   * POST JSON 并返回响应体字符串。
   *
   * @param url 完整 URL
   * @param headers 请求头
   * @param jsonBody 请求体（JSON）
   * @return 响应体字符串
   */
  String post(String url, Map<String, String> headers, String jsonBody);
}
