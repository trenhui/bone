package com.bone.claim.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.application.port.out.InsurerPushGatewayPort.InsurerPushMessage;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 保司报送真实 HTTP 出站适配器测试（本地 HttpServer 真发请求，不用 mock 网络）。 */
class InsurerPushGatewayPortAdapterTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  private InsurerPushGatewayPortAdapter adapter(int port, String token) {
    InsurerPushGatewayPortAdapter adapter = new InsurerPushGatewayPortAdapter();
    ReflectionTestUtils.setField(adapter, "endpoint", "http://127.0.0.1:" + port + "/push");
    ReflectionTestUtils.setField(adapter, "timeoutMs", 3000L);
    ReflectionTestUtils.setField(adapter, "authToken", token == null ? "" : token);
    return adapter;
  }

  private static InsurerPushMessage message() {
    return new InsurerPushMessage(
        0L, "ClaimCase", 42L, "INSURER_PUSH", "{\"claimNo\":\"CL1\",\"amount\":\"100.00\"}");
  }

  @Test
  void postsEnvelopeWithPayloadAndReturnsReceipt() throws IOException {
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> auth = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/push",
        ex -> {
          body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          auth.set(ex.getRequestHeaders().getFirst("Authorization"));
          byte[] out = "{\"receipt\":\"R-1\"}".getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, out.length);
          ex.getResponseBody().write(out);
          ex.close();
        });
    server.start();

    String receipt = adapter(server.getAddress().getPort(), "tok").push(message());

    assertThat(receipt).isEqualTo("R-1");
    assertThat(auth.get()).isEqualTo("Bearer tok");
    assertThat(body.get()).contains("\"eventType\":\"INSURER_PUSH\"");
    assertThat(body.get()).contains("\"claimNo\":\"CL1\"");
    assertThat(body.get()).contains("\"eventId\":\"INSURER_PUSH-0-ClaimCase-42\"");
  }

  @Test
  void throwsOnNon2xx() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/push",
        ex -> {
          byte[] out = "{\"error\":\"down\"}".getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(503, out.length);
          ex.getResponseBody().write(out);
          ex.close();
        });
    server.start();

    assertThatThrownBy(() -> adapter(server.getAddress().getPort(), "").push(message()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("非 2xx");
  }

  @Test
  void unconfiguredRejectsFakeDispatch() {
    InsurerPushGatewayPortAdapter adapter = new InsurerPushGatewayPortAdapter();
    ReflectionTestUtils.setField(adapter, "endpoint", "");
    assertThat(adapter.isConfigured()).isFalse();
    assertThatThrownBy(() -> adapter.push(message()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("端点未配置");
  }
}
