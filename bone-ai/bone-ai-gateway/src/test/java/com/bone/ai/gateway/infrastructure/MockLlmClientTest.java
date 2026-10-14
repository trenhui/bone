package com.bone.ai.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockLlmClientTest {

  private final MockLlmClient client = new MockLlmClient();

  @Test
  void chat_echoes_last_user_message_deterministically() {
    LlmRequest req =
        LlmRequest.of(
            "m", List.of(LlmMessage.system("sys"), LlmMessage.user("你好"), LlmMessage.user("世界")));
    LlmResponse resp = client.chat(req);

    assertThat(resp.content()).isEqualTo("MOCK[m]: 世界");
    assertThat(resp.model()).isEqualTo("m");
    assertThat(resp.usage().totalTokens()).isZero();
  }
}
