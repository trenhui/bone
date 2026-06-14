package com.bone.integration.infrastructure.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestClientImplTest {

  private HttpServer server;
  private String baseUrl;
  private final RestClientImpl client = new RestClientImpl();

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/health",
        exchange -> {
          byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.createContext(
        "/echo",
        exchange -> {
          byte[] body = "{\"received\":true}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(201, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void testConnection_returnsTrueOn2xx() {
    assertTrue(client.testConnection(Map.of("url", baseUrl + "/health")));
  }

  @Test
  void testConnection_returnsFalseOnBadUrl() {
    assertFalse(client.testConnection(Map.of("url", baseUrl + "/missing")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void sendRequest_returnsStatusAndBody() {
    Object result = client.sendRequest("/echo", Map.of("method", "GET"), Map.of("url", baseUrl));

    assertTrue(result instanceof Map);
    Map<String, Object> map = (Map<String, Object>) result;
    assertEquals(201, map.get("statusCode"));
    assertTrue(String.valueOf(map.get("body")).contains("received"));
  }
}
