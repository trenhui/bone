package com.bone.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 验证 Gateway 将 {@code /api/v1/generator/**} 转发至 studio-generator（含冒号动作路径）。 */
@SpringBootTest(
    classes = BoneGatewayApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GeneratorGatewayRouteIT {

  private static HttpServer mockGenerator;
  private static HttpServer deadExtension;
  private static HttpServer deadIam;
  private static HttpServer deadIntegration;
  private static int mockGeneratorPort;
  private static int deadExtensionPort;
  private static int deadIamPort;
  private static int deadIntegrationPort;
  private static final AtomicReference<String> lastPath = new AtomicReference<>();
  private static final AtomicReference<String> lastMethod = new AtomicReference<>();

  @Autowired private WebTestClient webTestClient;

  @BeforeAll
  static void startMocks() throws IOException {
    mockGenerator = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    mockGenerator.createContext("/", GeneratorGatewayRouteIT::handleGenerator);
    mockGenerator.start();
    mockGeneratorPort = mockGenerator.getAddress().getPort();

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

    deadIntegration = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    deadIntegration.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    deadIntegration.start();
    deadIntegrationPort = deadIntegration.getAddress().getPort();
  }

  @AfterAll
  static void stopMocks() {
    if (mockGenerator != null) {
      mockGenerator.stop(0);
    }
    if (deadExtension != null) {
      deadExtension.stop(0);
    }
    if (deadIam != null) {
      deadIam.stop(0);
    }
    if (deadIntegration != null) {
      deadIntegration.stop(0);
    }
  }

  @DynamicPropertySource
  static void registerUris(DynamicPropertyRegistry registry) {
    registry.add("generator.mock-uri", () -> "http://127.0.0.1:" + mockGeneratorPort);
    registry.add("extension.studio.mock-uri", () -> "http://127.0.0.1:" + deadExtensionPort);
    registry.add("iam.mock-uri", () -> "http://127.0.0.1:" + deadIamPort);
    registry.add("integration.mock-uri", () -> "http://127.0.0.1:" + deadIntegrationPort);
  }

  private static void handleGenerator(HttpExchange exchange) throws IOException {
    lastPath.set(exchange.getRequestURI().getPath());
    lastMethod.set(exchange.getRequestMethod());
    String path = exchange.getRequestURI().getPath();
    String method = exchange.getRequestMethod();
    byte[] body;
    int status = 200;
    if (path.endsWith("/data-sources") && "GET".equals(method)) {
      body =
          """
                    {"success":true,"code":200,"message":"ok","data":{"records":[],"total":0,"page":1,"size":10}}
                    """
              .getBytes(StandardCharsets.UTF_8);
    } else if (path.contains(":test-connection") && "POST".equals(method)) {
      body = "{\"success\":true,\"code\":200,\"data\":true}".getBytes(StandardCharsets.UTF_8);
    } else if (path.endsWith("/capabilities") && "GET".equals(method)) {
      body = "{\"success\":true,\"code\":200,\"data\":[]}".getBytes(StandardCharsets.UTF_8);
    } else if (path.endsWith("/code-generation") && "POST".equals(method)) {
      body =
          "{\"success\":true,\"code\":202,\"data\":{\"operationId\":\"op-gen-1\",\"taskId\":\"op-gen-1\"}}"
              .getBytes(StandardCharsets.UTF_8);
      status = 202;
      exchange.getResponseHeaders().add("Location", "/api/v1/generator/operations/op-gen-1");
    } else if (path.contains("/operations/") && "GET".equals(method)) {
      body =
          "{\"success\":true,\"code\":200,\"data\":{\"done\":true,\"progress\":100,\"result\":{\"status\":\"SUCCESS\"}}}"
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
  void routesDataSourcesListThroughGateway() {
    lastPath.set(null);
    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/api/v1/generator/data-sources")
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

    assertEquals("/api/v1/generator/data-sources", lastPath.get());
    assertEquals("GET", lastMethod.get());
  }

  @Test
  void routesTestConnectionColonActionThroughGateway() {
    lastPath.set(null);
    webTestClient
        .post()
        .uri("/api/v1/generator/data-sources/ds-1:test-connection")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data")
        .isEqualTo(true);

    assertNotNull(lastPath.get());
    assertTrue(lastPath.get().contains(":test-connection"));
    assertEquals("POST", lastMethod.get());
  }

  @Test
  void routesCapabilitiesThroughGateway() {
    lastPath.set(null);
    webTestClient
        .get()
        .uri("/api/v1/generator/capabilities")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true);

    assertEquals("/api/v1/generator/capabilities", lastPath.get());
  }

  @Test
  void routesCodeGenerationLroThroughGateway() {
    lastPath.set(null);
    webTestClient
        .post()
        .uri("/api/v1/generator/code-generation")
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectHeader()
        .exists("Location");

    webTestClient
        .get()
        .uri("/api/v1/generator/operations/op-gen-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.done")
        .isEqualTo(true);
  }
}
