package com.bone.ai.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.gateway.ResponseConfidence;
import com.bone.ai.gateway.domain.service.JsonFieldConfidence;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CascadeLlmClientTest {

  private final LlmClient cheap = mock(LlmClient.class);
  private final LlmClient strong = mock(LlmClient.class);
  private final ResponseConfidence confidence = new JsonFieldConfidence();
  private final CascadeLlmClient client =
      new CascadeLlmClient(cheap, strong, "qwen-max", 0.85, confidence);

  private final LlmRequest request =
      LlmRequest.of("qwen-turbo", List.of(LlmMessage.user("q")), "t");

  private LlmResponse resp(String content) {
    return new LlmResponse(content, "qwen-turbo", "stop", LlmUsage.empty());
  }

  @Test
  void high_confidence_stays_on_cheap_tier() {
    when(cheap.chat(request)).thenReturn(resp("{\"confidence\":0.95}"));

    assertThat(client.chat(request).content()).contains("0.95");
    verify(strong, never()).chat(any()); // 高置信不升级
  }

  @Test
  void low_confidence_escalates_to_strong_tier() {
    when(cheap.chat(request)).thenReturn(resp("{\"confidence\":0.4}"));
    when(strong.chat(any()))
        .thenReturn(new LlmResponse("{\"confidence\":0.99}", "qwen-max", "stop", LlmUsage.empty()));

    LlmResponse out = client.chat(request);

    assertThat(out.model()).isEqualTo("qwen-max"); // 用了强档位响应
    ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(strong).chat(captor.capture());
    assertThat(captor.getValue().model()).isEqualTo("qwen-max"); // 升级请求切到强档位
  }

  @Test
  void undeterminable_confidence_does_not_escalate() {
    // 无 confidence 字段 → -1 → 不升级（避免凭猜测双倍成本）
    when(cheap.chat(request)).thenReturn(resp("{\"answer\":\"maybe\"}"));

    assertThat(client.chat(request).content()).contains("maybe");
    verify(strong, never()).chat(any());
  }

  @Test
  void escalation_failure_falls_back_to_low_confidence_response() {
    when(cheap.chat(request)).thenReturn(resp("{\"confidence\":0.3}"));
    when(strong.chat(any())).thenThrow(new IllegalStateException("strong tier down"));

    // fail-soft：升级失败返回廉价低置信响应，交由下游闸门 / HITL，而非硬崩
    LlmResponse out = client.chat(request);
    assertThat(out.content()).contains("0.3");
  }
}
