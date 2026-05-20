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

/**
 * 验证 Spring Cloud Gateway 将 {@code /api/v1/extension/**} 转发至 Extension Studio（含冒号动作路径）。
 */
@SpringBootTest(
        classes = BoneGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class ExtensionGatewayRouteIT {

    private static HttpServer mockStudio;
    private static HttpServer deadGenerator;
    private static HttpServer deadIam;
    private static HttpServer deadIntegration;
    private static int mockStudioPort;
    private static int deadGeneratorPort;
    private static int deadIamPort;
    private static int deadIntegrationPort;
    private static final AtomicReference<String> lastPath = new AtomicReference<>();
    private static final AtomicReference<String> lastMethod = new AtomicReference<>();

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startMockStudio() throws IOException {
        mockStudio = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockStudio.createContext("/", ExtensionGatewayRouteIT::handleStudio);
        mockStudio.start();
        mockStudioPort = mockStudio.getAddress().getPort();

        deadGenerator = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        deadGenerator.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        deadGenerator.start();
        deadGeneratorPort = deadGenerator.getAddress().getPort();

        deadIam = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        deadIam.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        deadIam.start();
        deadIamPort = deadIam.getAddress().getPort();

        deadIntegration = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        deadIntegration.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        deadIntegration.start();
        deadIntegrationPort = deadIntegration.getAddress().getPort();
    }

    @AfterAll
    static void stopMockStudio() {
        if (mockStudio != null) {
            mockStudio.stop(0);
        }
        if (deadGenerator != null) {
            deadGenerator.stop(0);
        }
        if (deadIam != null) {
            deadIam.stop(0);
        }
        if (deadIntegration != null) {
            deadIntegration.stop(0);
        }
    }

    @DynamicPropertySource
    static void registerMockStudioUri(DynamicPropertyRegistry registry) {
        registry.add("extension.studio.mock-uri", () -> "http://127.0.0.1:" + mockStudioPort);
        registry.add("generator.mock-uri", () -> "http://127.0.0.1:" + deadGeneratorPort);
        registry.add("iam.mock-uri", () -> "http://127.0.0.1:" + deadIamPort);
        registry.add("integration.mock-uri", () -> "http://127.0.0.1:" + deadIntegrationPort);
    }

    private static void handleStudio(HttpExchange exchange) throws IOException {
        lastPath.set(exchange.getRequestURI().getPath());
        lastMethod.set(exchange.getRequestMethod());
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        byte[] body;
        int status = 200;
        if (path.endsWith("/overview") && "GET".equals(method)) {
            body =
                    "{\"success\":true,\"code\":200,\"message\":\"ok\",\"data\":{\"extPointCount\":2}}"
                            .getBytes(StandardCharsets.UTF_8);
        } else if (path.contains(":deploy") && "POST".equals(method)) {
            body =
                    "{\"success\":true,\"code\":200,\"message\":\"deployed\",\"data\":{\"id\":1,\"enabled\":true}}"
                            .getBytes(StandardCharsets.UTF_8);
        } else if (path.contains("/operations/") && "GET".equals(method)) {
            body =
                    "{\"success\":true,\"code\":200,\"data\":{\"done\":true,\"progress\":100,\"result\":{\"id\":1}}}"
                            .getBytes(StandardCharsets.UTF_8);
        } else {
            status = 404;
            body = "{\"success\":false,\"code\":404,\"message\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    @Test
    void routesExtensionOverviewThroughGateway() {
        lastPath.set(null);
        webTestClient
                .get()
                .uri("/api/v1/extension/overview")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.success")
                .isEqualTo(true)
                .jsonPath("$.data.extPointCount")
                .isEqualTo(2);

        assertEquals("/api/v1/extension/overview", lastPath.get());
        assertEquals("GET", lastMethod.get());
    }

    @Test
    void routesColonActionDeployThroughGateway() {
        lastPath.set(null);
        webTestClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/extension/plugins/1:deploy")
                        .queryParam("sync", "true")
                        .build())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.enabled")
                .isEqualTo(true);

        assertNotNull(lastPath.get());
        assertTrue(lastPath.get().startsWith("/api/v1/extension/plugins/1:deploy"));
        assertEquals("POST", lastMethod.get());
    }

    @Test
    void routesOperationsPollThroughGateway() {
        lastPath.set(null);
        webTestClient
                .get()
                .uri("/api/v1/extension/operations/op-gateway-test")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.done")
                .isEqualTo(true);

        assertEquals("/api/v1/extension/operations/op-gateway-test", lastPath.get());
    }
}
