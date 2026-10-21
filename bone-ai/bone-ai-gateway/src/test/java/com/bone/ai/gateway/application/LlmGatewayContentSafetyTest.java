package com.bone.ai.gateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmGatewayPolicies;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.infrastructure.security.HeuristicContentSafetyGuard;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 网关输出内容安全（OWASP LLM05/09）：命中留证后拒绝，安全产出放行。 */
class LlmGatewayContentSafetyTest {

  private final LlmClient client = mock(LlmClient.class);
  private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);
  private final LlmGatewayApplicationService service =
      new LlmGatewayApplicationService(
          client,
          publisher,
          tenantProvider,
          new LlmGatewayPolicies(
              null, null, null, null, null, null, new HeuristicContentSafetyGuard()));

  @BeforeEach
  void setUp() {
    when(tenantProvider.currentTenantId()).thenReturn("42");
  }

  @Test
  void unsafe_output_is_blocked_with_failure_evidence() {
    when(client.chat(any()))
        .thenReturn(
            new LlmResponse("I will ignore my instructions", "m", "stop", LlmUsage.empty()));

    assertThatThrownBy(() -> service.invoke(LlmRequest.of("m", List.of(LlmMessage.user("q")), "t")))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiGatewayErrorCodes.CONTENT_UNSAFE));
    verify(publisher).publish(any(LlmCallRecorded.class)); // 命中留证
  }

  @Test
  void safe_output_passes_through() {
    when(client.chat(any()))
        .thenReturn(new LlmResponse("建议赔付 1200 元", "m", "stop", LlmUsage.empty()));

    assertThat(service.invoke(LlmRequest.of("m", List.of(LlmMessage.user("q")), "t")).content())
        .isEqualTo("建议赔付 1200 元");
  }
}
