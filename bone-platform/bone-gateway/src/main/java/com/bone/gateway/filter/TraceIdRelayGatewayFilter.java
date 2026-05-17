package com.bone.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 透传 / 生成 X-Request-Id、X-Trace-Id，对齐 Bone-API-规范 §10。 */
@Component
public class TraceIdRelayGatewayFilter implements GlobalFilter, Ordered {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String REQUEST_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = resolveTraceId(request);
        ServerHttpRequest mutated =
                request.mutate().header(TRACE_HEADER, traceId).header(REQUEST_HEADER, traceId).build();
        exchange.getResponse().getHeaders().add(TRACE_HEADER, traceId);
        exchange.getResponse().getHeaders().add(REQUEST_HEADER, traceId);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private static String resolveTraceId(ServerHttpRequest request) {
        String fromTrace = request.getHeaders().getFirst(TRACE_HEADER);
        if (StringUtils.hasText(fromTrace)) {
            return fromTrace.trim();
        }
        String fromRequest = request.getHeaders().getFirst(REQUEST_HEADER);
        if (StringUtils.hasText(fromRequest)) {
            return fromRequest.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
