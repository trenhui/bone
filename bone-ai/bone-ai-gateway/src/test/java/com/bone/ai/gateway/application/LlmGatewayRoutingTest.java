package com.bone.ai.gateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmGatewayPolicies;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.gateway.ModelRouter;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.infrastructure.routing.ConfigDrivenModelRouter;
import com.bone.core.domain.event.DomainEventPublisher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 网关场景 → 模型档位路由（成本红线 / 降档）：命中映射用档位模型，否则按请求模型原样调用。 */
class LlmGatewayRoutingTest {

  private final LlmClient client = mock(LlmClient.class);
  private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);
  private final ModelRouter router =
      new ConfigDrivenModelRouter(Map.of("claim-assessor", "qwen-turbo"));

  private LlmGatewayApplicationService service;

  @BeforeEach
  void setUp() {
    when(tenantProvider.currentTenantId()).thenReturn("42");
    when(client.chat(any())).thenReturn(new LlmResponse("ok", "m", "stop", LlmUsage.empty()));
    service =
        new LlmGatewayApplicationService(
            client, publisher, tenantProvider, LlmGatewayPolicies.permissive(), router);
  }

  private String capturedModel(LlmRequest req) {
    service.invoke(req);
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(client).chat(captor.capture());
    return captor.getValue().model();
  }

  @Test
  void scenario_routes_to_configured_model_tier() {
    String model =
        capturedModel(
            LlmRequest.of("claim-assessor", List.of(LlmMessage.user("q")), "t")
                .withScenario("claim-assessor"));
    assertThat(model).isEqualTo("qwen-turbo"); // 档位模型覆盖请求模型
  }

  @Test
  void no_scenario_keeps_requested_model() {
    String model =
        capturedModel(LlmRequest.of("claim-assessor", List.of(LlmMessage.user("q")), "t"));
    assertThat(model).isEqualTo("claim-assessor"); // 不参与路由
  }

  @Test
  void unmapped_scenario_keeps_requested_model() {
    String model =
        capturedModel(
            LlmRequest.of("claim-assessor", List.of(LlmMessage.user("q")), "t")
                .withScenario("other-scene"));
    assertThat(model).isEqualTo("claim-assessor");
  }
}
