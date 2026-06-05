package com.bone.system.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.system.testsupport.MetadataSdkIntegrationTestConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * 控制台方法级鉴权 · 真实 HTTP（非 MockMvc）E2E 验证。
 *
 * <p>用 {@code RANDOM_PORT} 启动整个 Spring Boot Web 容器，{@link TestRestTemplate}
 * 通过 TCP 真打 HTTP 请求，覆盖完整的 Filter Chain → SecurityConfig → @PreAuthorize 路径，
 * 输出真实状态码与报文（详设 §5.0 As-Is 验证证据）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MetadataSdkIntegrationTestConfiguration.class)
class ConsoleSecurityHttpE2ETest {

    @Autowired
    private TestRestTemplate http;

    @Value("${bone.iam.jwt.secret-key}")
    private String secretKey;

    @Test
    void overview_without_token_returns_401_with_body() {
        ResponseEntity<String> resp = http.getForEntity("/api/v1/console/overview", String.class);
        System.out.println("[HTTP-401] status=" + resp.getStatusCode() + " body=" + resp.getBody());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void overview_with_wrong_scope_returns_403() {
        String token = issueToken(List.of("iam:accounts:read"));
        ResponseEntity<String> resp = exchangeWithBearer("/api/v1/console/overview", token);
        System.out.println("[HTTP-403] status=" + resp.getStatusCode() + " body=" + resp.getBody());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void overview_with_correct_scope_returns_200() {
        String token = issueToken(List.of("sys:console:read"));
        ResponseEntity<String> resp = exchangeWithBearer("/api/v1/console/overview", token);
        System.out.println("[HTTP-200] status=" + resp.getStatusCode() + " body=" + resp.getBody());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"code\":200").contains("services");
    }

    @Test
    void quick_actions_with_correct_scope_returns_200() {
        String token = issueToken(List.of("sys:console:read"));
        ResponseEntity<String> resp = exchangeWithBearer("/api/v1/console/quick-actions", token);
        System.out.println("[HTTP-200 quick-actions] status=" + resp.getStatusCode() + " body=" + resp.getBody());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"code\":200");
    }

    @Test
    void actuator_health_is_public() {
        ResponseEntity<String> resp = http.getForEntity("/actuator/health", String.class);
        System.out.println("[HTTP actuator/health] status=" + resp.getStatusCode() + " body=" + resp.getBody());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void metrics_with_correct_scope_returns_200() {
        String token = issueToken(List.of("sys:console:read"));
        ResponseEntity<String> resp = exchangeWithBearer("/api/v1/console/metrics", token);
        System.out.println("[HTTP-200 metrics] status=" + resp.getStatusCode() + " body=" + resp.getBody());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> exchangeWithBearer(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String issueToken(List<String> scopes) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("admin")
                .claim("userId", "1")
                .claim("tenantId", "0")
                .claim("scopes", scopes)
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(key)
                .compact();
    }
}
