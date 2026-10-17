package com.bone.ai.gateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.infrastructure.security.RegexPiiRedactor;
import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 网关出口脱敏（OWASP LLM02）：出站请求与落证内容均不得含未脱敏 PII。 */
class LlmGatewayApplicationServiceRedactionTest {

  private static final String ID_CARD = "11010519491231002X";

  private final LlmClient client = mock(LlmClient.class);
  private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);

  private LlmGatewayApplicationService service(boolean redactionEnabled) {
    return new LlmGatewayApplicationService(
        client, publisher, tenantProvider, new RegexPiiRedactor(redactionEnabled));
  }

  private void respondWith(String content) {
    when(client.chat(any()))
        .thenReturn(new LlmResponse(content, "m", "stop", new LlmUsage(10, 20, 30)));
  }

  private String capturedOutbound() {
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(client).chat(captor.capture());
    return captor.getValue().messages().get(0).content();
  }

  private LlmCallRecorded capturedCallEvent() {
    ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(publisher, atLeastOnce()).publish(captor.capture());
    return captor.getAllValues().stream()
        .filter(LlmCallRecorded.class::isInstance)
        .map(LlmCallRecorded.class::cast)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void outbound_request_is_redacted_before_reaching_model() {
    respondWith("已核验");
    LlmRequest request = LlmRequest.of("m", List.of(LlmMessage.user("核验身份证 " + ID_CARD)));

    service(true).invoke(request);

    String sent = capturedOutbound();
    assertThat(sent).doesNotContain(ID_CARD);
    assertThat(sent).contains("[身份证]");
  }

  @Test
  void evidence_snapshot_request_and_response_are_both_redacted() {
    respondWith("核验通过：" + ID_CARD + " 与保单一致");
    LlmRequest request = LlmRequest.of("m", List.of(LlmMessage.user("核验身份证 " + ID_CARD)));

    service(true).invoke(request);

    LlmCallRecorded recorded = capturedCallEvent();
    assertThat(recorded.request().messages().get(0).content()).doesNotContain(ID_CARD);
    // 模型可能回显输入里的 PII：证据表不得成为未脱敏副本
    assertThat(recorded.response().content()).doesNotContain(ID_CARD);
  }

  @Test
  void caller_still_receives_original_response() {
    respondWith("核验通过：" + ID_CARD + " 与保单一致");
    LlmRequest request = LlmRequest.of("m", List.of(LlmMessage.user("q")));

    LlmResponse response = service(true).invoke(request);

    // 业务判定逻辑不变：返回原文，只有出站与落证脱敏
    assertThat(response.content()).contains(ID_CARD);
  }

  @Test
  void disabled_redaction_passes_through_unchanged() {
    respondWith("ok");
    LlmRequest request = LlmRequest.of("m", List.of(LlmMessage.user("身份证 " + ID_CARD)));

    service(false).invoke(request);

    assertThat(capturedOutbound()).isEqualTo("身份证 " + ID_CARD);
  }
}
