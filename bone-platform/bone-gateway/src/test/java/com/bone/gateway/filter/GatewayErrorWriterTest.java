package com.bone.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.gateway.config.GatewayErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/**
 * 网关错误信封契约（G-3）：字段与平台 {@code ApiResponse} 一致（success/code/message/data/timestamp）+ {@code
 * errorCode}，Jackson 序列化、无手写拼串。
 */
class GatewayErrorWriterTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  @DisplayName("401 响应体：success=false、code=401、带 errorCode 与 timestamp，data=null")
  void envelopeMatchesPlatformApiResponseShape() throws Exception {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/iam/whoami").build());

    GatewayErrorWriter.write(
            exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCodes.UNAUTHORIZED, "未授权：无效或缺失 token")
        .block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    String json = exchange.getResponse().getBodyAsString().block();
    JsonNode node = mapper.readTree(json);
    assertThat(node.get("success").asBoolean()).isFalse();
    assertThat(node.get("code").asInt()).isEqualTo(401);
    assertThat(node.get("message").asText()).contains("未授权");
    assertThat(node.get("errorCode").asText()).isEqualTo("GW_UNAUTHORIZED");
    assertThat(node.get("timestamp").asText()).isNotBlank();
    assertThat(node.has("data")).isTrue();
    assertThat(node.get("data").isNull()).isTrue();
    // G-8：不再有手写转义面；traceId 走响应头而非 body。
    assertThat(node.has("traceId")).isFalse();
  }

  @Test
  @DisplayName("429/503：状态与码透传，Content-Type 为 JSON")
  void statusAndCodePassThroughForRateLimitAndBreaker() throws Exception {
    for (var scenario :
        new Object[][] {
          {HttpStatus.TOO_MANY_REQUESTS, GatewayErrorCodes.RATE_LIMITED},
          {HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorCodes.UPSTREAM_UNAVAILABLE}
        }) {
      HttpStatus status = (HttpStatus) scenario[0];
      String code = (String) scenario[1];
      MockServerWebExchange exchange =
          MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
      GatewayErrorWriter.write(exchange, status, code, "msg").block();
      assertThat(exchange.getResponse().getStatusCode()).isEqualTo(status);
      JsonNode node = mapper.readTree(exchange.getResponse().getBodyAsString().block());
      assertThat(node.get("errorCode").asText()).isEqualTo(code);
      assertThat(node.get("code").asInt()).isEqualTo(status.value());
    }
  }

  @Test
  @DisplayName("message 含引号/换行时 Jackson 正确转义（G-8 消除）")
  void specialCharactersAreEscapedByJackson() throws Exception {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());
    GatewayErrorWriter.write(
            exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCodes.UNAUTHORIZED, "a\"b\r\nX")
        .block();
    JsonNode node = mapper.readTree(exchange.getResponse().getBodyAsString().block());
    assertThat(node.get("message").asText()).isEqualTo("a\"b\r\nX");
  }
}
