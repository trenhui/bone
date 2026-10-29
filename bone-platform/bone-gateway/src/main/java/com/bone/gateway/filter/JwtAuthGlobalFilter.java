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
 * 网关统一 JWT 鉴权：校验签名/过期，失败返 401；通过后解析 tenantId/userId/scopes 注入 X-Tenant-Id / X-User-Id / X-Roles /
 * X-Operator-Id Header，并保留原 Authorization 透传（下游仍各自验签）。 order 高于 TraceId
 * 之后（TraceId=HIGHEST_PRECEDENCE）。
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

  /**
   * 内部身份头黑名单：入站一律剥离，只允许网关按 token claim 重新写入。
   *
   * <p>这些头在下游被当作「已认证身份」直接使用（租户数据源路由、MDC 用户维度、审计字段）， 若放行客户端取值，等于把身份判断交给调用方。新增同类头时<b>必须</b>登记在此。
   */
  private static final List<String> INTERNAL_IDENTITY_HEADERS =
      List.of("X-Tenant-Id", "X-User-Id", "X-Roles", "X-Operator-Id");

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
      return GatewayErrorWriter.write(
          exchange,
          HttpStatus.UNAUTHORIZED,
          com.bone.gateway.config.GatewayErrorCodes.UNAUTHORIZED,
          "未授权：无效或缺失 token");
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
    // X-Operator-Id 同理：它是「操作人」这一审计主体的旧载体，任何入站取值都等于让调用方自证身份，
    // 进而绕过双人复核的「复核人 ≠ 第一审核人」（职责分离）。身份一律由下方 token claim 派生。
    builder.headers(
        h -> {
          for (String internal : INTERNAL_IDENTITY_HEADERS) {
            h.remove(internal);
          }
        });
    if (p.getTenantId() != null) {
      builder.header("X-Tenant-Id", p.getTenantId());
    }
    if (p.getUserId() != null) {
      builder.header("X-User-Id", p.getUserId());
      // X-Operator-Id 必须由网关**重新写入**，不能只剥离不补。
      // 工作流设计器的写路径（保存草稿 / 发布 / 上下线）用 @RequestHeader("X-Operator-Id")（required）
      // 取审计操作人：上面剥离掉客户端取值后若不补，下游收到的就是「header 不存在」，
      // 设计器写接口恒失败（实测：保存草稿 500 / 发布 500，读路径却全正常，极易误判成设计器自身故障）。
      // 取值取 userId（token claim，稳定且改名不失效），与 X-User-Id 同源——两者是同一主体的不同消费口径。
      // 安全性质不变：客户端送来的同名头仍被无条件下剥离，下游只可能读到网关派生值。
      builder.header("X-Operator-Id", p.getUserId());
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
