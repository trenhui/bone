package com.bone.claim.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.config.LlmGatewayProperties;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.claim.application.port.out.ClaimContextPort;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 「探活用的模型」必须等于「业务用的模型」。
 *
 * <p>这条用例是一次真实事故的钉子：业务侧把逻辑别名 {@code synap-claim-ai} 当模型名发出去，探活却用配置里的模型名。 于是一个配好真实 provider
 * 的环境会出现——探活 {@code REACHABLE}、每个案件却 {@code GATEWAY_ERROR} 全量降级。 上游的报错是 {@code
 * invalid_request_error: The supported API model names are ...}，而网关把它收敛成一句 {@code
 * GATEWAY_ERROR}，从两侧都看不出真正原因。
 *
 * <p>铁律：两侧共用 {@link AiGatewayPortAdapter#requestModel}。任何一侧自行决定模型名，都会让探活的绿灯失去意义——
 * 绿灯要能代表业务路径，否则它只是在证明「某个别的模型通」。
 */
class AiModelResolutionTest {

  private static final ClaimContextPort.ClaimContextView SAMPLE =
      new ClaimContextPort.ClaimContextView(
          1001L, "MEDICAL", new BigDecimal("3200.00"), true, true, new BigDecimal("0.95"), true);

  private static LlmGatewayProperties propsWith(String model) {
    LlmGatewayProperties properties = new LlmGatewayProperties();
    properties.setBaseUrl("https://provider.invalid/v1");
    properties.setApiKey("k");
    properties.setModel(model);
    return properties;
  }

  @Test
  @DisplayName("业务请求携带配置的模型名，而不是逻辑别名")
  void businessRequestUsesConfiguredModel() {
    LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
    when(gateway.invoke(any()))
        .thenReturn(
            new LlmResponse("{\"rejectSuggested\":false,\"confidence\":0.9}", "m", null, null));

    new AiGatewayPortAdapter(gateway, propsWith("deepseek-chat")).assess(SAMPLE);

    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(gateway).invoke(captor.capture());
    assertThat(captor.getValue().model()).isEqualTo("deepseek-chat");
  }

  @Test
  @DisplayName("未配置模型名时回落逻辑别名（Mock 路径不看该字段）")
  void blankModelFallsBackToAlias() {
    assertThat(AiGatewayPortAdapter.requestModel(propsWith("  ")))
        .isEqualTo(AiGatewayPortAdapter.MODEL);
    assertThat(AiGatewayPortAdapter.requestModel(propsWith(null)))
        .isEqualTo(AiGatewayPortAdapter.MODEL);
  }

  @Test
  @DisplayName("探活与业务解析出同一个模型名（同真源，不是各写一套）")
  void probeAndBusinessAgreeOnModel() {
    LlmGatewayProperties properties = propsWith("deepseek-chat");
    LlmGatewayApplicationService gateway = mock(LlmGatewayApplicationService.class);
    when(gateway.invoke(any())).thenReturn(new LlmResponse("{\"ok\":true}", "m", null, null));

    new AiProviderProbeAdapter(gateway, properties).probe();
    new AiGatewayPortAdapter(gateway, properties).assess(SAMPLE);

    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(gateway, org.mockito.Mockito.times(2)).invoke(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(LlmRequest::model)
        .containsOnly(AiGatewayPortAdapter.requestModel(properties));
  }
}
