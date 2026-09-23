package com.bone.gateway.filter;

import com.bone.gateway.config.GatewayJwtProperties;
import com.bone.gateway.security.GatewayJwtUtil;
import com.bone.gateway.security.GatewayJwtUtil.GatewayPrincipal;
import java.util.List;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一 JWT 鉴权：校验签名/过期，失败返 401；通过后解析 tenantId/userId/scopes 注入 X-Tenant-Id / X-User-Id / X-Roles
 * Header，并保留原 Authorization 透传（下游仍各自验签）。 order 高于 TraceId 之后（TraceId=HIGHEST_PRECEDENCE）。
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

  private final GatewayJwtProperties properties;
  private final GatewayJwtUtil jwtUtil;

  public JwtAuthGlobalFilter(GatewayJwtProperties properties, GatewayJwtUtil jwtUtil) {
    this.properties = properties;
    this.jwtUtil = jwtUtil;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();
    HttpMethod method = request.getMethod();

    // CORS 预检 + 白名单免校验
    if (HttpMethod.OPTIONS.equals(method) || isWhitelisted(path)) {
      return chain.filter(exchange);
    }

    String authorization = request.getHeaders().getFirst(properties.getHeaderName());
    Optional<GatewayPrincipal> principal = jwtUtil.parse(authorization);
    if (principal.isEmpty()) {
      return GatewayErrorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "未授权：无效或缺失 token");
    }

    GatewayPrincipal p = principal.get();
    ServerHttpRequest mutated = mutateWithPrincipal(request, p);
    return chain.filter(exchange.mutate().request(mutated).build());
  }

  private boolean isWhitelisted(String path) {
    for (String prefix : properties.getWhitelist()) {
      if (prefix != null && !prefix.isBlank() && path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private ServerHttpRequest mutateWithPrincipal(ServerHttpRequest request, GatewayPrincipal p) {
    ServerHttpRequest.Builder builder = request.mutate();
    // 先清除客户端自带的可信头，避免伪造 X-Tenant-Id/X-User-Id/X-Roles 被下游误读（租户伪造向量）
    builder.headers(
        h -> {
          h.remove("X-Tenant-Id");
          h.remove("X-User-Id");
          h.remove("X-Roles");
        });
    if (p.getTenantId() != null) {
      builder.header("X-Tenant-Id", p.getTenantId());
    }
    if (p.getUserId() != null) {
      builder.header("X-User-Id", p.getUserId());
    }
    List<String> scopes = p.getScopes();
    if (scopes != null && !scopes.isEmpty()) {
      builder.header("X-Roles", String.join(",", scopes));
    }
    // 保留原 Authorization（已在校验时读取）
    return builder.build();
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
