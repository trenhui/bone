package com.bone.ai.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.core.exception.BizException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleClientTest {

  private String seenUrl;
  private String seenBody;
  private final Map<String, String> seenHeaders = new LinkedHashMap<>();

  private final OpenAiCompatibleClient client =
      new OpenAiCompatibleClient(
          (url, headers, body) -> {
            seenUrl = url;
            seenHeaders.putAll(headers);
            seenBody = body;
            return "{\"model\":\"gpt-4o-mini\",\"choices\":[{\"message\":{\"content\":\"你好\"},"
                + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":3,"
                + "\"completion_tokens\":2,\"total_tokens\":5}}";
          },
          "https://api.example.com/v1/",
          "sk-test",
          "gpt-4o-mini");

  @Test
  void chat_builds_openai_request_and_parses_response() {
    LlmRequest req =
        LlmRequest.of("gpt-4o-mini", List.of(LlmMessage.system("s"), LlmMessage.user("u")));
    LlmResponse resp = client.chat(req);

    assertThat(seenUrl).isEqualTo("https://api.example.com/v1/chat/completions");
    assertThat(seenHeaders).containsEntry("Authorization", "Bearer sk-test");
    assertThat(seenHeaders).containsEntry("Content-Type", "application/json");
    assertThat(seenBody).contains("\"role\":\"user\"").contains("\"model\":\"gpt-4o-mini\"");

    assertThat(resp.content()).isEqualTo("你好");
    assertThat(resp.model()).isEqualTo("gpt-4o-mini");
    assertThat(resp.finishReason()).isEqualTo("stop");
    assertThat(resp.usage().promptTokens()).isEqualTo(3);
    assertThat(resp.usage().completionTokens()).isEqualTo(2);
    assertThat(resp.usage().totalTokens()).isEqualTo(5);
  }

  @Test
  void chat_omits_authorization_when_api_key_blank() {
    Map<String, String> captured = new LinkedHashMap<>();
    OpenAiCompatibleClient noKey =
        new OpenAiCompatibleClient(
            (u, h, b) -> {
              captured.putAll(h);
              return "{\"choices\":[{\"message\":{\"content\":\"x\"}}]}";
            },
            "http://localhost:8000/v1",
            "",
            "m");
    LlmResponse resp = noKey.chat(LlmRequest.of("m", List.of(LlmMessage.user("u"))));

    assertThat(resp.content()).isEqualTo("x");
    assertThat(resp.usage().totalTokens()).isZero();
    assertThat(captured).doesNotContainKey("Authorization");
  }

  @Test
  void empty_choices_is_rejected_not_silently_empty() {
    // provider 限流 / 错误页 / 截断都会返回无 choices 的 body——
    // 不能靠 asText("")+finish 默认值把它伪装成「正常的空响应」（静默坏数据）
    LlmHttpTransport stub = (url, headers, body) -> "{\"model\":\"m\",\"choices\":[]}";
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(stub, "http://llm", "k", "m");

    assertThatThrownBy(() -> client.chat(LlmRequest.of("m", List.of(LlmMessage.user("q")))))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiGatewayErrorCodes.PROVIDER_RESPONSE_INVALID));
  }

  @Test
  void malformed_json_is_rejected_with_stable_code() {
    LlmHttpTransport stub = (url, headers, body) -> "<html>502 Bad Gateway</html>";
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(stub, "http://llm", "k", "m");

    assertThatThrownBy(() -> client.chat(LlmRequest.of("m", List.of(LlmMessage.user("q")))))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiGatewayErrorCodes.PROVIDER_RESPONSE_INVALID));
  }
}
