package com.bone.ai.gateway.infrastructure;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;

/**
 * 确定性 Mock 客户端：未配置真实 provider（{@code bone.ai.gateway.base-url}）时的默认实现。
 *
 * <p>本地开发 / 单测 / 降级可用，输出可复现（回显最后一条 user 消息）。
 */
public class MockLlmClient implements LlmClient {

  @Override
  public LlmResponse chat(LlmRequest request) {
    String last = "";
    for (LlmMessage m : request.messages()) {
      if ("user".equals(m.role())) {
        last = m.content();
      }
    }
    return new LlmResponse(
        "MOCK[" + request.model() + "]: " + last, request.model(), "stop", LlmUsage.empty());
  }
}
