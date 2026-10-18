package com.bone.ai.gateway.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 请求契约不变量（fail-fast）：非法参数在构造期拒绝，而不是带到 provider 才拿回语义模糊的 400。
 *
 * <p>价值：早失败 + 错误指回调用点。若放到 provider，一次网络往返后只拿到 "invalid temperature" 这类与业务无关的信息。
 */
class LlmContractInvariantsTest {

  private static final List<LlmMessage> MESSAGES = List.of(LlmMessage.user("q"));

  private static LlmRequest request(Double temperature, Integer maxTokens) {
    return new LlmRequest("m", MESSAGES, temperature, maxTokens, false, false, null);
  }

  @Test
  void accepts_valid_temperature_range() {
    assertThatCode(() -> request(0.0, null)).doesNotThrowAnyException();
    assertThatCode(() -> request(2.0, null)).doesNotThrowAnyException();
  }

  @Test
  void rejects_out_of_range_temperature() {
    assertThatThrownBy(() -> request(2.5, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("temperature");
    assertThatThrownBy(() -> request(-0.1, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_non_positive_max_tokens() {
    assertThatThrownBy(() -> request(null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxTokens");
    assertThatThrownBy(() -> request(null, -5)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void accepts_null_temperature_and_max_tokens() {
    // null = 不指定，由 provider 用默认值——不是非法值
    assertThatCode(() -> request(null, null)).doesNotThrowAnyException();
  }

  @Test
  void accepts_protocol_roles() {
    assertThatCode(() -> LlmMessage.system("s")).doesNotThrowAnyException();
    assertThatCode(() -> LlmMessage.user("u")).doesNotThrowAnyException();
    assertThatCode(() -> LlmMessage.assistant("a")).doesNotThrowAnyException();
  }

  @Test
  void rejects_unknown_role() {
    assertThatThrownBy(() -> new LlmMessage("tool", "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("role");
  }

  @Test
  void rejects_blank_content() {
    assertThatThrownBy(() -> LlmMessage.user("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("content");
    assertThatThrownBy(() -> LlmMessage.user(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void null_messages_normalized_to_empty_list() {
    assertThat(LlmRequest.of("m", null).messages()).isEmpty();
  }
}
