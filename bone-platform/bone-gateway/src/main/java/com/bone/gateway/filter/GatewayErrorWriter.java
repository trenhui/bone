package com.bone.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关错误响应写入工具（WebFlux）。
 *
 * <p><b>信封契约（G-3，设计稿 §4.1）</b>：网关是前端唯一入口，错误体必须与平台 {@code ApiResponse} 字段一致——{@code success / code /
 * message / data / timestamp}——并附加 {@code errorCode} 供前端 i18n。 历史实现手写 JSON 拼串（无
 * success/timestamp、转义不全），前端统一解析器无法处理，本类改为 Jackson 序列化。
 *
 * <p><b>为何本地 record 而非直接依赖 {@code com.bone.core.model.ApiResponse}</b>：bone-gateway 无 bone-core
 * 依赖（WebFlux 独立栈），新增依赖属 L3 需架构师审批；本 record 与 ApiResponse 字段逐一对应，若 ApiResponse 演进字段，此处测试会钉住契约不漂移。
 *
 * <p><b>traceId 不进 body</b>：由 {@code TraceIdRelayGatewayFilter} 写入响应头 {@code X-Trace-Id}，与平台
 * 链路口径一致（ Bone-API-规范 §10）。
 */
public final class GatewayErrorWriter {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private GatewayErrorWriter() {}

  /** 与平台信封同形的错误体。字段名必须与 {@code com.bone.core.model.ApiResponse} 一致，另附 {@code errorCode}。 */
  public record GatewayErrorResp(
      boolean success, int code, String message, Object data, String errorCode, String timestamp) {}

  public static Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    GatewayErrorResp body =
        new GatewayErrorResp(
            false, status.value(), message, null, errorCode, Instant.now().toString());
    byte[] bytes;
    try {
      bytes = MAPPER.writeValueAsBytes(body);
    } catch (Exception e) {
      // 序列化失败不可能因固定字段发生；兜底保证响应仍带状态码，不吞掉原始错误语义。
      bytes =
          ("{\"success\":false,\"code\":"
                  + status.value()
                  + ",\"message\":\"gateway error\",\"data\":null,\"errorCode\":\""
                  + errorCode
                  + "\",\"timestamp\":\""
                  + Instant.now()
                  + "\"}")
              .getBytes(StandardCharsets.UTF_8);
    }
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }
}
