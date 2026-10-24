package com.bone.ai.gateway.infrastructure;

import java.util.Map;
import java.util.stream.Stream;

/**
 * LLM 出站 HTTP 传输 seam：把「发请求」与「构造 / 解析」解耦。
 *
 * <p>用途：① 单测用假传输覆盖真实链路（不连网）；② 为后续熔断 / 重试 / 计量留出替换位，与仓内 {@code RestClientImpl} 的 Resilience4j
 * 熔断思路一致。
 *
 * <p><b>流式（2026 LLM 网关标配）</b>：{@link #streamPost} 返回 SSE {@code data:} 载荷流（已剔除 {@code
 * [DONE]}）。默认实现是 把 {@link #post} 整段响应当作单元素流（缓冲兜底）——不支持真流式的传输（如测试假传输、被熔断装饰器包裹前的直连）天然回落「缓冲式流式」，调用方
 * API 不变； 真实 SSE 由 {@code JdkLlmTransport} 覆盖。
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

  /**
   * 流式 POST，返回 SSE {@code data:} 载荷流（每个元素是一段完整 JSON，不含 {@code [DONE]}）。
   *
   * <p>默认把 {@link #post} 整段响应当作单元素流——无真流式能力的传输自动回落「缓冲式流式」，客户端与调用方零改动；真实 SSE 由实现类覆盖。
   */
  default Stream<String> streamPost(String url, Map<String, String> headers, String jsonBody) {
    return Stream.of(post(url, headers, jsonBody));
  }
}
