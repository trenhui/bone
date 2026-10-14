package com.bone.ai.gateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmGatewayServiceTest {

  private final LlmClient client = mock(LlmClient.class);
  private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
  private final LlmGatewayService service = new LlmGatewayService(client, publisher);

  @Test
  void invoke_delegates_to_client_and_publishes_event_when_no_redline() {
    LlmRequest req = LlmRequest.of("m", List.of(LlmMessage.user("hi")));
    LlmResponse resp = new LlmResponse("ok", "m", "stop", LlmUsage.empty());
    when(client.chat(req)).thenReturn(resp);

    assertThat(service.invoke(req)).isSameAs(resp);
    verify(client).chat(req);
    verify(publisher).publish(any(LlmCallRecorded.class));
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
