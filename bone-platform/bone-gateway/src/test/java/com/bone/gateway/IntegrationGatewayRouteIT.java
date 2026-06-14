package com.bone.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 验证 Gateway 将 {@code /api/v1/integration/**} 转发至 bone-integration。 */
@SpringBootTest(
    classes = BoneGatewayApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class IntegrationGatewayRouteIT {

  private static HttpServer mockIntegration;
  private static HttpServer deadExtension;
  private static HttpServer deadIam;
  private static HttpServer deadGenerator;
  private static int mockIntegrationPort;
  private static int deadExtensionPort;
  private static int deadIamPort;
  private static int deadGeneratorPort;
  private static final AtomicReference<String> lastPath = new AtomicReference<>();
  private static final AtomicReference<String> lastMethod = new AtomicReference<>();

  @Autowired private WebTestClient webTestClient;

  @BeforeAll
  static void startMocks() throws IOException {
    mockIntegration = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    mockIntegration.createContext("/", IntegrationGatewayRouteIT::handleIntegration);
    mockIntegration.start();
    mockIntegrationPort = mockIntegration.getAddress().getPort();

    deadExtension = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    deadExtension.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    deadExtension.start();
    deadExtensionPort = deadExtension.getAddress().getPort();

    deadIam = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    deadIam.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    deadIam.start();
    deadIamPort = deadIam.getAddress().getPort();

    deadGenerator = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    deadGenerator.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    deadGenerator.start();
    deadGeneratorPort = deadGenerator.getAddress().getPort();
  }

  @AfterAll
  static void stopMocks() {
    if (mockIntegration != null) {
      mockIntegration.stop(0);
    }
    if (deadExtension != null) {
      deadExtension.stop(0);
    }
    if (deadIam != null) {
      deadIam.stop(0);
    }
    if (deadGenerator != null) {
      deadGenerator.stop(0);
    }
  }

  @DynamicPropertySource
  static void registerUris(DynamicPropertyRegistry registry) {
    registry.add("integration.mock-uri", () -> "http://127.0.0.1:" + mockIntegrationPort);
    registry.add("extension.studio.mock-uri", () -> "http://127.0.0.1:" + deadExtensionPort);
    registry.add("iam.mock-uri", () -> "http://127.0.0.1:" + deadIamPort);
    registry.add("generator.mock-uri", () -> "http://127.0.0.1:" + deadGeneratorPort);
  }

  private static void handleIntegration(HttpExchange exchange) throws IOException {
    lastPath.set(exchange.getRequestURI().getPath());
    lastMethod.set(exchange.getRequestMethod());
    String path = exchange.getRequestURI().getPath();
    String method = exchange.getRequestMethod();
    byte[] body;
    int status = 200;
    if (path.endsWith("/flows") && "GET".equals(method)) {
      body =
          """
                    {"success":true,"code":200,"message":"ok","data":{"records":[],"total":0,"page":1,"size":10}}
                    """
              .getBytes(StandardCharsets.UTF_8);
    } else if (path.endsWith("/executions") && "POST".equals(method)) {
      body = "{\"success\":true,\"code\":200,\"data\":10001}".getBytes(StandardCharsets.UTF_8);
    } else if (path.endsWith("/statistics") && "GET".equals(method)) {
      body =
          "{\"success\":true,\"code\":200,\"data\":{\"successRate\":1.0}}"
              .getBytes(StandardCharsets.UTF_8);
    } else {
      status = 404;
      body = "{\"success\":false,\"code\":404}".getBytes(StandardCharsets.UTF_8);
    }
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(body);
    }
  }

  @Test
  void routesFlowsListThroughGateway() {
    lastPath.set(null);
    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/api/v1/integration/flows")
                    .queryParam("page", "1")
                    .queryParam("size", "10")
                    .build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.data.records")
        .isArray();

    assertEquals("/api/v1/integration/flows", lastPath.get());
    assertEquals("GET", lastMethod.get());
  }

  @Test
  void routesExecuteFlowThroughGateway() {
    lastPath.set(null);
    webTestClient
        .post()
        .uri("/api/v1/integration/executions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"flowId\":1,\"inputData\":{}}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data")
        .isEqualTo(10001);

    assertNotNull(lastPath.get());
    assertEquals("/api/v1/integration/executions", lastPath.get());
    assertEquals("POST", lastMethod.get());
  }

  @Test
  void routesStatisticsThroughGateway() {
    lastPath.set(null);
    webTestClient
        .get()
        .uri("/api/v1/integration/statistics")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.successRate")
        .isEqualTo(1.0);

    assertEquals("/api/v1/integration/statistics", lastPath.get());
  }
}
