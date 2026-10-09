package com.bone.gateway.filter;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关是南北向 CORS 的唯一边界：浏览器 ↔ 网关 的跨域由 {@code globalcors} 处理并回写 {@code Access-Control-Allow-Origin}。但
 * Spring Cloud Gateway 默认会把浏览器带来的 {@code Origin}/{@code Access-Control-Request-*} 请求头原样透传给下游服务；下游（如
 * IAM/System/File） 各自又配了一份 localhost 白名单的 CORS，于是用 {@code 127.0.0.1:3000} 打开 Shell 时，网关这一侧
 * 已放行，请求却卡在下游的 CORS 上返回 403 "Invalid CORS request"，整站无法登录。
 *
 * <p>本过滤器在路由转发前剔除浏览器侧 CORS 请求头，使下游把请求当作同源处理、不再做跨域校验。 仅作用于「经网关」的流量；后端服务被独立 dev 直连（不经网关）时仍走其自身 CORS
 * 配置，互不影响。 order 取 0：晚于 JWT 鉴权（HIGHEST_PRECEDENCE+10），早于实际转发（WebClient 路由 filter 极低优先级）。
 */
@Component
public class StripBrowserCorsHeadersFilter implements GlobalFilter, Ordered {

  private static final List<String> BROWSER_CORS_HEADERS =
      List.of(
          HttpHeaders.ORIGIN,
          HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
          HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerWebExchange stripped =
        exchange
            .mutate()
            .request(
                exchange
                    .getRequest()
                    .mutate()
                    .headers(h -> BROWSER_CORS_HEADERS.forEach(h::remove))
                    .build())
            .build();
    return chain.filter(stripped);
  }

  @Override
  public int getOrder() {
    return 0;
  }
}
