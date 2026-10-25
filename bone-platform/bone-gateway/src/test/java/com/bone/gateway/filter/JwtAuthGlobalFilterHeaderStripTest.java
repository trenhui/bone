package com.bone.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.gateway.config.GatewayJwtProperties;
import com.bone.gateway.security.GatewayJwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关入站身份头剥离：客户端自带的 {@code X-Tenant-Id} / {@code X-User-Id} / {@code X-Roles} / {@code
 * X-Operator-Id} 必须在网关被丢弃，并按 token claim 重新写入。
 *
 * <p>这是「操作人不可自证」的第一道闸：下游（理赔等）已改为只认 token，但只要有任意一个模块还读头， 放行伪造值就等于把身份判断交给调用方。故把剥离行为固化成测试，而不是靠约定。
 */
class JwtAuthGlobalFilterHeaderStripTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef";
  private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

  private static GatewayJwtProperties properties() {
    GatewayJwtProperties props = new GatewayJwtProperties();
    props.setSecretKey(SECRET);
    return props;
  }

  /** 与 IAM 同构的 token：sub=用户名，userId/tenantId/scopes 为自定义 claim。 */
  private static String token(String userId, String tenantId, List<String> scopes) {
    return "Bearer "
        + Jwts.builder()
            .subject("admin")
            .claim("userId", userId)
            .claim("tenantId", tenantId)
            .claim("scopes", scopes)
            .signWith(KEY)
            .compact();
  }

  /** 跑一遍过滤器，返回下游真正收到的请求头。 */
  private static HttpHeaders downstreamHeaders(MockServerHttpRequest request) {
    GatewayJwtProperties props = properties();
    JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(props, new GatewayJwtUtil(props));
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    filter
        .filter(
            exchange,
            ex -> {
              forwarded.set(ex);
              return Mono.empty();
            })
        .block();
    return forwarded.get().getRequest().getHeaders();
  }

  @Test
  @DisplayName("伪造身份头被丢弃，身份按 token claim 重建")
  void forgedIdentityHeadersAreDropped() {
    MockServerHttpRequest request =
        MockServerHttpRequest.post("/api/v1/claims")
            .header("Authorization", token("42", "0", List.of("claim:claims:read")))
            .header("X-User-Id", "forged-user")
            .header("X-Tenant-Id", "999")
            .header("X-Roles", "claim:review:write,iam:accounts:write")
            .header("X-Operator-Id", "forged-operator")
            .build();

    HttpHeaders headers = downstreamHeaders(request);

    assertThat(headers.getFirst("X-User-Id")).isEqualTo("42");
    assertThat(headers.getFirst("X-Tenant-Id")).isEqualTo("0");
    assertThat(headers.getFirst("X-Roles")).isEqualTo("claim:claims:read");
    assertThat(headers.containsKey("X-Operator-Id")).isFalse();
  }

  @Test
  @DisplayName("X-Operator-Id 无论是否携带 token 都不会透传到下游")
  void operatorHeaderNeverPassesThrough() {
    MockServerHttpRequest request =
        MockServerHttpRequest.post("/api/v1/claims")
            .header("Authorization", token("7", "0", List.of("claim:review:write")))
            .header("X-Operator-Id", "someone-else")
            .build();

    HttpHeaders headers = downstreamHeaders(request);

    assertThat(headers.containsKey("X-Operator-Id")).isFalse();
    // 身份只可能来自 token
    assertThat(headers.getFirst("X-User-Id")).isEqualTo("7");
  }

  @Test
  @DisplayName("白名单路径（登录/健康检查）不受影响")
  void whitelistedPathPassesThrough() {
    MockServerHttpRequest request =
        MockServerHttpRequest.post("/api/v1/iam/login")
            .header("X-Operator-Id", "still-here")
            .build();

    HttpHeaders headers = downstreamHeaders(request);

    // 免校验路径不重写身份头（登录本就无身份），断言只为确认过滤器没有被误加逻辑
    assertThat(headers.containsKey("X-User-Id")).isFalse();
  }
}
