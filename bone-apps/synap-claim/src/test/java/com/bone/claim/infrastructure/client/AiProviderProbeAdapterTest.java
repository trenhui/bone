package com.bone.claim.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.application.LlmGatewayApplicationService;
import com.bone.ai.gateway.config.LlmGatewayProperties;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.claim.application.port.out.AiProviderProbePort.ProviderProbe;
import com.bone.claim.domain.model.decision.AiDegradationReason;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 探活的判据：<b>「配了」不等于「通了」，更不等于「契约对」</b>。
 *
 * <p>三个红灯各自对应一种真实事故，且处置动作互斥：
 *
 * <ul>
 *   <li>{@code MOCK_NOT_ALLOWED}——配了 base-url 却仍是替身：装配没生效；
 *   <li>{@code OUTPUT_CONTRACT_VIOLATION}——HTTP 通了但模型不吐 JSON：这是「探活绿灯、业务全降级」的最坏组合，必须红灯；
 *   <li>{@code PROVIDER_TIMEOUT} / {@code GATEWAY_ERROR}——网络与鉴权，看 provider 侧。
 * </ul>
 *
 * <p>另一条不许被破坏的性质：<b>未配置时不发请求</b>。若这里真发一次，探活会既花一次调用、又把「没配」报成「通了」。
 */
class AiProviderProbeAdapterTest {

  private LlmGatewayApplicationService gateway;
  private LlmGatewayProperties properties;
  private AiProviderProbeAdapter adapter;

  @BeforeEach
  void setUp() {
    gateway = mock(LlmGatewayApplicationService.class);
    properties = new LlmGatewayProperties();
    adapter = new AiProviderProbeAdapter(gateway, properties);
  }

  private void configure() {
    properties.setBaseUrl("https://api.example.invalid/v1");
    properties.setApiKey("k");
    properties.setModel("test-model");
  }

  @Test
  @DisplayName("未配置 base-url：不触碰网关，直接报 configured=false")
  void notConfiguredDoesNotCallGateway() {
    properties.setBaseUrl("  ");

    ProviderProbe probe = adapter.probe();

    assertThat(probe.configured()).isFalse();
    assertThat(probe.reachable()).isFalse();
    assertThat(probe.reason()).isNull();
    verifyNoInteractions(gateway);
  }

  @Test
  @DisplayName("符合 JSON 契约：reachable=true + 上游模型标识")
  void reachableWithProviderModel() {
    configure();
    when(gateway.invoke(any()))
        .thenReturn(new LlmResponse("{\"ok\":true}", "deepseek-chat", "stop", null));

    ProviderProbe probe = adapter.probe();

    assertThat(probe.configured()).isTrue();
    assertThat(probe.reachable()).isTrue();
    assertThat(probe.model()).isEqualTo("deepseek-chat");
    assertThat(probe.reason()).isNull();
  }

  @Test
  @DisplayName("上游不回模型标识：回落逻辑别名，不返回空串")
  void missingModelFallsBackToAlias() {
    configure();
    when(gateway.invoke(any())).thenReturn(new LlmResponse("{\"ok\":true}", "", null, null));

    assertThat(adapter.probe().model()).isEqualTo(AiProviderProbeAdapter.PROBE_MODEL_ALIAS);
  }

  @Test
  @DisplayName("配了却拿到替身响应：MOCK_NOT_ALLOWED（装配未生效，不是「通」）")
  void mockResponseIsRejected() {
    configure();
    when(gateway.invoke(any()))
        .thenReturn(new LlmResponse("{\"mock\":true}", "mock-llm", "stop", null));

    ProviderProbe probe = adapter.probe();

    assertThat(probe.reachable()).isFalse();
    assertThat(probe.reason()).isEqualTo(AiDegradationReason.MOCK_NOT_ALLOWED.name());
  }

  @Test
  @DisplayName("HTTP 通但不吐 JSON：OUTPUT_CONTRACT_VIOLATION（探活绿灯+业务全降级的最坏组合）")
  void nonJsonContentIsContractViolation() {
    configure();
    when(gateway.invoke(any()))
        .thenReturn(new LlmResponse("Sure, here is the JSON:", "m", null, null));

    ProviderProbe probe = adapter.probe();

    assertThat(probe.reachable()).isFalse();
    assertThat(probe.reason()).isEqualTo(AiDegradationReason.OUTPUT_CONTRACT_VIOLATION.name());
  }

  @Test
  @DisplayName("异常链里有超时类型：PROVIDER_TIMEOUT（与「其他网关故障」处置不同）")
  void timeoutIsClassifiedFromCauseChain() {
    configure();
    when(gateway.invoke(any()))
        .thenThrow(
            new RuntimeException("transport failed", new SocketTimeoutException("read timed out")));

    assertThat(adapter.probe().reason()).isEqualTo(AiDegradationReason.PROVIDER_TIMEOUT.name());
  }

  @Test
  @DisplayName("其余网关异常：GATEWAY_ERROR（不按消息文本猜鉴权/限流）")
  void otherFailureIsGatewayError() {
    configure();
    when(gateway.invoke(any())).thenThrow(new RuntimeException("401 unauthorized"));

    assertThat(adapter.probe().reason()).isEqualTo(AiDegradationReason.GATEWAY_ERROR.name());
  }
}
