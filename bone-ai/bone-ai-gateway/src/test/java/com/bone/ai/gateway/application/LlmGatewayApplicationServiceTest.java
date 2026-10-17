package com.bone.ai.gateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.event.LlmUsageRecorded;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmGatewayApplicationServiceTest {

  private final LlmClient client = mock(LlmClient.class);
  private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);
  private final LlmGatewayApplicationService service =
      new LlmGatewayApplicationService(client, publisher, tenantProvider);

  @Test
  void invoke_delegates_to_client_and_publishes_event_when_no_redline() {
    LlmRequest req = LlmRequest.of("m", List.of(LlmMessage.user("hi")), "trace-1");
    LlmResponse resp = new LlmResponse("ok", "m", "stop", LlmUsage.empty());
    when(client.chat(req)).thenReturn(resp);
    when(tenantProvider.currentTenantId()).thenReturn("42");

    assertThat(service.invoke(req)).isSameAs(resp);
    verify(client).chat(req);
    verify(publisher).publish(any(LlmCallRecorded.class));
    verify(publisher).publish(any(LlmUsageRecorded.class)); // 计量事件与证据链分离发布
  }

  @Test
  void invoke_publishes_failure_evidence_when_client_fails() {
    // 失败调用也留证（详设 §2.1 执行链第 5 步「含失败」）：事故复盘直接证据
    LlmRequest req = LlmRequest.of("m", List.of(LlmMessage.user("hi")), "trace-2");
    when(client.chat(req)).thenThrow(new IllegalStateException("provider down"));

    assertThatThrownBy(() -> service.invoke(req))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provider down");
    verify(publisher).publish(any(LlmCallRecorded.class));
    // 失败无用量，不发布计量事件
    verify(publisher, never()).publish(any(LlmUsageRecorded.class));
  }

  @Test
  void invoke_blocks_and_never_calls_client_or_publishes_when_rule_short_circuited() {
    LlmRequest req = LlmRequest.of("m", List.of(LlmMessage.user("hi"))).withRuleShortCircuit();

    assertThatThrownBy(() -> service.invoke(req))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("成本红线");
    // 负向探针：红线命中时绝不触碰模型客户端、也不发布事件
    verifyNoInteractions(client);
    verifyNoInteractions(publisher);
  }
}
