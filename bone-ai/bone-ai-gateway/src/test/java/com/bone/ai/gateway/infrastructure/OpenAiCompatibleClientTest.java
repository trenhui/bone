package com.bone.ai.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmStreamEvent;
import com.bone.core.exception.BizException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
          () -> "sk-test",
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
            () -> "",
            "m");
    LlmResponse resp = noKey.chat(LlmRequest.of("m", List.of(LlmMessage.user("u"))));

    assertThat(resp.content()).isEqualTo("x");
    assertThat(resp.usage().totalTokens()).isZero();
    assertThat(captured).doesNotContainKey("Authorization");
  }

  @Test
  void api_key_resolved_per_call_so_rotation_takes_effect_without_restart() {
    // 密钥轮换 / 短时限 Virtual Key 的核心语义：provider 侧换 key 后，同一客户端实例下一次调用即用新 key，
    // 无需重启或重装配（构造期固化的老实现做不到——换 key 必须重启）
    List<String> keysSeen = new java.util.ArrayList<>();
    Map<String, String> captured = new LinkedHashMap<>();
    OpenAiCompatibleClient rotating =
        new OpenAiCompatibleClient(
            (u, h, b) -> {
              captured.clear();
              captured.putAll(h);
              return "{\"choices\":[{\"message\":{\"content\":\"x\"}}]}";
            },
            "https://api.example.com/v1",
            () -> {
              String key = keysSeen.isEmpty() ? "sk-old" : "sk-new";
              keysSeen.add(key);
              return key;
            },
            "m");

    rotating.chat(LlmRequest.of("m", List.of(LlmMessage.user("u"))));
    assertThat(captured).containsEntry("Authorization", "Bearer sk-old");

    rotating.chat(LlmRequest.of("m", List.of(LlmMessage.user("u"))));
    assertThat(captured).containsEntry("Authorization", "Bearer sk-new");
  }

  @Test
  void null_api_key_provider_is_tolerated_local_no_auth_path() {
    // 本地无鉴权 provider（Ollama）组装链路可能不配密钥源：null provider 不带 Authorization 头而非 NPE
    Map<String, String> captured = new LinkedHashMap<>();
    OpenAiCompatibleClient noProvider =
        new OpenAiCompatibleClient(
            (u, h, b) -> {
              captured.putAll(h);
              return "{\"choices\":[{\"message\":{\"content\":\"x\"}}]}";
            },
            "http://localhost:11434/v1",
            null,
            "m");

    LlmResponse resp = noProvider.chat(LlmRequest.of("m", List.of(LlmMessage.user("u"))));

    assertThat(resp.content()).isEqualTo("x");
    assertThat(captured).doesNotContainKey("Authorization");
  }

  @Test
  void empty_choices_is_rejected_not_silently_empty() {
    // provider 限流 / 错误页 / 截断都会返回无 choices 的 body——
    // 不能靠 asText("")+finish 默认值把它伪装成「正常的空响应」（静默坏数据）
    LlmHttpTransport stub = (url, headers, body) -> "{\"model\":\"m\",\"choices\":[]}";
    OpenAiCompatibleClient client =
        new OpenAiCompatibleClient(stub, "http://llm", () -> "k", "m");

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
    OpenAiCompatibleClient client =
        new OpenAiCompatibleClient(stub, "http://llm", () -> "k", "m");

    assertThatThrownBy(() -> client.chat(LlmRequest.of("m", List.of(LlmMessage.user("q")))))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiGatewayErrorCodes.PROVIDER_RESPONSE_INVALID));
  }

  @Test
  void streamChat_emits_tokens_in_order_and_done_with_usage() {
    List<String> payloads =
        List.of(
            "{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}",
            "{\"choices\":[{\"delta\":{\"content\":\"世界\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}");
    OpenAiCompatibleClient client =
        new OpenAiCompatibleClient(new FakeStreamingTransport(payloads), "https://api.example.com/v1", () -> "sk-test", "gpt-4o-mini");

    List<LlmStreamEvent> events = new ArrayList<>();
    client.streamChat(LlmRequest.of("gpt-4o-mini", List.of(LlmMessage.user("u"))), events::add);

    assertThat(events).hasSize(3);
    assertThat(events.get(0)).isInstanceOf(LlmStreamEvent.Token.class).extracting("delta").isEqualTo("你好");
    assertThat(events.get(1)).isInstanceOf(LlmStreamEvent.Token.class).extracting("delta").isEqualTo("世界");
    assertThat(events.get(2))
        .isInstanceOf(LlmStreamEvent.Done.class)
        .satisfies(
            d -> {
              LlmStreamEvent.Done done = (LlmStreamEvent.Done) d;
              assertThat(done.usage().totalTokens()).isEqualTo(5);
            });
  }

  @Test
  void streamChat_uses_stream_true_and_include_usage_in_body() {
    FakeStreamingTransport transport =
        new FakeStreamingTransport(List.of("{\"choices\":[{\"delta\":{\"content\":\"x\"}}]}"));
    OpenAiCompatibleClient client =
        new OpenAiCompatibleClient(transport, "https://api.example.com/v1", () -> "sk-test", "m");

    client.streamChat(LlmRequest.of("m", List.of(LlmMessage.user("u"))), e -> {});

    assertThat(transport.capturedBody).contains("\"stream\":true");
    assertThat(transport.capturedBody).contains("\"include_usage\":true");
  }

  @Test
  void streamChat_falls_back_to_buffered_when_transport_not_streaming() {
    // 不支持真流式的传输（如测试假传输、被熔断装饰器包裹前的直连）走默认 streamPost：把整段响应当单元素流
    LlmHttpTransport buffered = (url, headers, body) -> "{\"choices\":[{\"message\":{\"content\":\"x\"}}]}";
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(buffered, "http://llm", () -> "k", "m");

    List<LlmStreamEvent> events = new ArrayList<>();
    client.streamChat(LlmRequest.of("m", List.of(LlmMessage.user("u"))), events::add);

    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(LlmStreamEvent.Token.class).extracting("delta").isEqualTo("x");
    assertThat(events.get(1)).isInstanceOf(LlmStreamEvent.Done.class);
  }

  @Test
  void streamChat_emits_error_on_provider_error_payload() {
    List<String> payloads = List.of("{\"error\":{\"message\":\"rate limited\",\"type\":\"rate_limit\"}}");
    OpenAiCompatibleClient client =
        new OpenAiCompatibleClient(new FakeStreamingTransport(payloads), "https://api.example.com/v1", () -> "sk-test", "m");

    List<LlmStreamEvent> events = new ArrayList<>();
    client.streamChat(LlmRequest.of("m", List.of(LlmMessage.user("u"))), events::add);

    assertThat(events).hasSize(1);
    assertThat(events.get(0)).isInstanceOf(LlmStreamEvent.Error.class);
    assertThat(events.get(0).toString()).contains("rate limited");
  }

  /** 测试假传输：post 与 streamPost 各自捕获请求体；streamPost 返回预设的 SSE 载荷流。 */
  static final class FakeStreamingTransport implements LlmHttpTransport {
    String capturedBody;
    private final List<String> payloads;

    FakeStreamingTransport(List<String> payloads) {
      this.payloads = payloads;
    }

    @Override
    public String post(String url, Map<String, String> headers, String jsonBody) {
      capturedBody = jsonBody;
      return "{}";
    }

    @Override
    public Stream<String> streamPost(String url, Map<String, String> headers, String jsonBody) {
      capturedBody = jsonBody;
      return payloads.stream();
    }
  }
}
