package com.bone.ai.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 最小多模态扩展：带图消息序列化为 content-parts，纯文本消息保持字符串 content 不变。 */
class OpenAiCompatibleClientMultimodalTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 捕获请求体的假传输层（与生产 OpenAiCompatibleClient 的调用面一致）。 */
  static final class CapturingTransport implements LlmHttpTransport {
    String lastBody;

    @Override
    public String post(String url, Map<String, String> headers, String body) {
      this.lastBody = body;
      return "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
          + "\"usage\":{\"total_tokens\":1}}";
    }

    @Override
    public java.util.stream.Stream<String> streamPost(
        String url, Map<String, String> headers, String body) {
      return java.util.stream.Stream.empty();
    }
  }

  @Test
  @DisplayName("带图 user 消息 → [{type:text},{type:image_url,image_url:{url}}]")
  void imageMessageSerializedAsContentParts() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(transport, "http://x", null, "m");

    client.chat(
        new LlmRequest(
            "m",
            List.of(
                LlmMessage.system("sys"),
                LlmMessage.userImage("看图抽取字段", "https://example.com/1.png")),
            null,
            null,
            false,
            false,
            "t1",
            null));

    JsonNode root = MAPPER.readTree(transport.lastBody);
    JsonNode user = root.path("messages").get(1);
    assertThat(user.path("content").isArray()).isTrue();
    assertThat(user.path("content").get(0).path("type").asText()).isEqualTo("text");
    assertThat(user.path("content").get(0).path("text").asText()).isEqualTo("看图抽取字段");
    assertThat(user.path("content").get(1).path("type").asText()).isEqualTo("image_url");
    assertThat(user.path("content").get(1).path("image_url").path("url").asText())
        .isEqualTo("https://example.com/1.png");
  }

  @Test
  @DisplayName("向后兼容：2 参构造/纯文本消息 content 仍为字符串，imageUrl 为 null")
  void textMessageUnchanged() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(transport, "http://x", null, "m");

    assertThat(LlmMessage.user("hi").imageUrl()).isNull();
    client.chat(
        new LlmRequest(
            "m",
            List.of(LlmMessage.system("sys"), LlmMessage.user("纯文本")),
            null,
            null,
            false,
            false,
            "t2",
            null));

    JsonNode root = MAPPER.readTree(transport.lastBody);
    assertThat(root.path("messages").get(1).path("content").isTextual()).isTrue();
    assertThat(root.path("messages").get(1).path("content").asText()).isEqualTo("纯文本");
  }
}
