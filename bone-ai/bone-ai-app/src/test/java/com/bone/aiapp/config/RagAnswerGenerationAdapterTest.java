package com.bone.aiapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.airag.domain.gateway.AnswerGenerationException;
import com.bone.core.exception.BizException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** R1「接 G」组合根适配器回归：生成必须经网关门面（治理链 / 计量 / 证据链），故障保真透传 errorCode。 */
class RagAnswerGenerationAdapterTest {

  private final LlmGatewayApplicationService gatewayService =
      mock(LlmGatewayApplicationService.class);
  private final BoneAiProperties properties = new BoneAiProperties();
  private final RagAnswerGenerationAdapter adapter =
      new RagAnswerGenerationAdapter(gatewayService, properties);

  @Test
  void delegates_to_gateway_and_returns_raw_model_content() {
    when(gatewayService.invoke(any()))
        .thenReturn(new LlmResponse("逾期付款按日千分之一[1]。", "gpt-4o-mini", "stop", null));

    String answer = adapter.generate("系统提示", "用户提示（围栏上下文）");

    // 模型原文必须原样返回：接地校验针对的就是模型真实输出，适配器不得裁剪改写
    assertThat(answer).isEqualTo("逾期付款按日千分之一[1]。");

    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(gatewayService).invoke(captor.capture());
    LlmRequest request = captor.getValue();
    assertThat(request.model()).isEqualTo("gpt-4o-mini");
    assertThat(request.messages()).hasSize(2);
    assertThat(request.messages().get(0).role()).isEqualTo("system");
    assertThat(request.messages().get(0).content()).isEqualTo("系统提示");
    assertThat(request.messages().get(1).role()).isEqualTo("user");
    assertThat(request.messages().get(1).content()).isEqualTo("用户提示（围栏上下文）");
  }

  @Test
  void uses_configured_answer_model() {
    when(gatewayService.invoke(any())).thenReturn(new LlmResponse("答案", "qwen-plus", "stop", null));
    properties.setRagAnswerModel("qwen-plus");

    adapter.generate("s", "u");

    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(gatewayService).invoke(captor.capture());
    assertThat(captor.getValue().model()).isEqualTo("qwen-plus");
  }

  @Test
  void maps_governance_rejection_to_port_failure_preserving_error_code() {
    // 网关头号风险（OWASP LLM01）：注入疑似拒绝的 errorCode 必须保真透传——
    // 归一成「服务不可用」会让安全事件从监控里消失
    when(gatewayService.invoke(any()))
        .thenThrow(new BizException(400, "疑似提示注入", "AI_GATEWAY_INJECTION_SUSPECTED"));

    assertThatThrownBy(() -> adapter.generate("s", "u"))
        .isInstanceOf(AnswerGenerationException.class)
        .hasFieldOrPropertyWithValue("errorCode", "AI_GATEWAY_INJECTION_SUSPECTED")
        .hasFieldOrPropertyWithValue("code", 400);
  }
}
