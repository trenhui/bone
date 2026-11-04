package com.bone.ai.gateway.infrastructure;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 确定性 Mock 客户端：未配置真实 provider（{@code bone.ai.gateway.base-url}）时的默认实现。
 *
 * <p>本地开发 / 单测 / 降级可用，输出可复现（回显最后一条 user 消息）。
 *
 * <p><b>生产契约（G0-2）</b>：测试替身必须遵守生产契约（Google Rules of ML #5「独立于 ML 测试基础设施」、 ML Test Score
 * 基础设施类）。{@link LlmRequest#jsonResponse()} 为 true 时，网关 {@code JsonOutputValidator} 与下游消费方均按裸 JSON
 * 解析，故本实现必须返回 JSON 对象，而非自然语言回显。
 *
 * <p>同时以 {@code mock:true} <b>自报身份</b>：消费方据此显式拒绝替身结论进入自动决策，而不是依赖「恰好解析失败」 这种偶然行为降级（Rules of ML
 * #10「警惕静默失败」）。未配 provider 时系统应当明确告知运维，而非静默产出 一个看似成功、实则无置信度的退化结论。
 *
 * <p>新增 {@link LlmClient} 实现时须一并纳入 {@code LlmClientJsonContractTest} 的契约断言。
 */
public class MockLlmClient implements LlmClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public LlmResponse chat(LlmRequest request) {
    String last = "";
    for (LlmMessage m : request.messages()) {
      if ("user".equals(m.role())) {
        last = m.content();
      }
    }
    String content =
        request.jsonResponse()
            ? jsonEnvelope(request.model(), last)
            : "MOCK[" + request.model() + "]: " + last;
    return new LlmResponse(content, request.model(), "stop", LlmUsage.empty());
  }

  private static String jsonEnvelope(String model, String echo) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("mock", true);
    body.put("model", model);
    body.put("echo", echo);
    try {
      return MAPPER.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Mock 结构化响应构造失败", e);
    }
  }
}
