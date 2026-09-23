package com.bone.gateway.filter;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 网关错误响应写入工具（WebFlux）。 */
public final class GatewayErrorWriter {

  private GatewayErrorWriter() {}

  public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    // 透传 X-Trace-Id（由 TraceIdRelayGatewayFilter 注入 response header），便于排障关联
    String traceId = response.getHeaders().getFirst("X-Trace-Id");
    String body =
        "{\"code\":"
            + status.value()
            + ",\"message\":\""
            + escape(message)
            + "\""
            + (traceId != null ? ",\"traceId\":\"" + escape(traceId) + "\"" : "")
            + "}";
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }

  private static String escape(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
