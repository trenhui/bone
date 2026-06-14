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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 验证 Gateway 将 {@code /api/v1/iam/**} 转发至 bone-iam。 */
@SpringBootTest(
    classes = BoneGatewayApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class IamGatewayRouteIT {

  private static HttpServer mockIam;
  private static HttpServer deadExtension;
  private static HttpServer deadGenerator;
  private static HttpServer deadIntegration;
  private static int mockIamPort;
  private static int deadExtensionPort;
  private static int deadGeneratorPort;
  private static int deadIntegrationPort;
  private static final AtomicReference<String> lastPath = new AtomicReference<>();
  private static final AtomicReference<String> lastMethod = new AtomicReference<>();

  @Autowired private WebTestClient webTestClient;

  @BeforeAll
  static void startMocks() throws IOException {
    mockIam = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    mockIam.createContext("/", IamGatewayRouteIT::handleIam);
    mockIam.start();
    mockIamPort = mockIam.getAddress().getPort();

    deadExtension = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    deadExtension.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    deadExtension.start();
    deadExtensionPort = deadExtension.getAddress().getPort();

    deadGenerator = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    deadGenerator.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(404, -1);
          exchange.close();
        });
    deadGenerator.start();
    deadGeneratorPort = deadGenerator.getAddress().getPort();

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
    if (mockIam != null) {
      mockIam.stop(0);
    }
    if (deadExtension != null) {
      deadExtension.stop(0);
    }
    if (deadGenerator != null) {
      deadGenerator.stop(0);
    }
    if (deadIntegration != null) {
      deadIntegration.stop(0);
    }
  }

  @DynamicPropertySource
  static void registerUris(DynamicPropertyRegistry registry) {
    registry.add("iam.mock-uri", () -> "http://127.0.0.1:" + mockIamPort);
    registry.add("extension.studio.mock-uri", () -> "http://127.0.0.1:" + deadExtensionPort);
    registry.add("generator.mock-uri", () -> "http://127.0.0.1:" + deadGeneratorPort);
    registry.add("integration.mock-uri", () -> "http://127.0.0.1:" + deadIntegrationPort);
  }

  private static void handleIam(HttpExchange exchange) throws IOException {
    lastPath.set(exchange.getRequestURI().getPath());
    lastMethod.set(exchange.getRequestMethod());
    String path = exchange.getRequestURI().getPath();
    String method = exchange.getRequestMethod();
    byte[] body;
    int status = 200;
    if (path.endsWith("/accounts") && "GET".equals(method)) {
      body =
          """
                    {"success":true,"code":200,"message":"ok","data":{"records":[],"total":0,"page":1,"size":10}}
                    """
              .getBytes(StandardCharsets.UTF_8);
    } else if (path.matches(".*/roles/[^/]+$") && "GET".equals(method)) {
      body =
          """
                    {"success":true,"code":200,"data":{"id":1,"name":"admin","permissionCodes":["iam:read"]}}
                    """
              .getBytes(StandardCharsets.UTF_8);
    } else if (path.endsWith("/login") && "POST".equals(method)) {
      body =
          "{\"success\":true,\"code\":200,\"data\":{\"token\":\"mock-token\",\"expiresIn\":3600}}"
              .getBytes(StandardCharsets.UTF_8);
    } else if (path.contains("/accounts/") && path.endsWith("/enable") && "POST".equals(method)) {
      body = "{\"success\":true,\"code\":200,\"data\":true}".getBytes(StandardCharsets.UTF_8);
    } else if (path.endsWith("/sso/config") && "GET".equals(method)) {
      body =
          "{\"success\":true,\"code\":200,\"data\":{\"enabled\":false}}"
              .getBytes(StandardCharsets.UTF_8);
    } else if (path.endsWith("/mfa/status") && "GET".equals(method)) {
      body =
          "{\"success\":true,\"code\":200,\"data\":{\"enabled\":false,\"enrolled\":false}}"
              .getBytes(StandardCharsets.UTF_8);
    } else if (path.endsWith("/audit/logs") && "GET".equals(method)) {
      body =
          """
                    {"success":true,"code":200,"data":{"records":[],"total":0,"page":1,"size":20}}
                    """
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
  void routesAccountsListThroughGateway() {
    lastPath.set(null);
    webTestClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/api/v1/iam/accounts")
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

    assertEquals("/api/v1/iam/accounts", lastPath.get());
    assertEquals("GET", lastMethod.get());
  }

  @Test
  void routesRoleDetailThroughGateway() {
    lastPath.set(null);
    webTestClient
        .get()
        .uri("/api/v1/iam/roles/1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.name")
        .isEqualTo("admin");

    assertEquals("/api/v1/iam/roles/1", lastPath.get());
  }

  @Test
  void routesLoginThroughGateway() {
    lastPath.set(null);
    webTestClient
        .post()
        .uri("/api/v1/iam/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"admin\",\"password\":\"123456\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.token")
        .isEqualTo("mock-token");

    assertEquals("/api/v1/iam/login", lastPath.get());
    assertEquals("POST", lastMethod.get());
  }

  @Test
  void routesAccountEnableThroughGateway() {
    lastPath.set(null);
    webTestClient
        .post()
        .uri("/api/v1/iam/accounts/42/enable")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data")
        .isEqualTo(true);

    assertNotNull(lastPath.get());
    assertTrue(lastPath.get().endsWith("/accounts/42/enable"));
  }

  @Test
  void routesMfaStatusThroughGateway() {
    lastPath.set(null);
    webTestClient
        .get()
        .uri("/api/v1/iam/mfa/status")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.enabled")
        .isEqualTo(false);

    assertEquals("/api/v1/iam/mfa/status", lastPath.get());
  }

  @Test
  void routesSsoConfigThroughGateway() {
    lastPath.set(null);
    webTestClient
        .get()
        .uri("/api/v1/iam/sso/config")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.enabled")
        .isEqualTo(false);

    assertEquals("/api/v1/iam/sso/config", lastPath.get());
    assertEquals("GET", lastMethod.get());
  }

  @Test
  void routesAuditLogsThroughGateway() {
    lastPath.set(null);
    webTestClient
        .get()
        .uri("/api/v1/iam/audit/logs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data.records")
        .isArray();

    assertEquals("/api/v1/iam/audit/logs", lastPath.get());
  }
}
