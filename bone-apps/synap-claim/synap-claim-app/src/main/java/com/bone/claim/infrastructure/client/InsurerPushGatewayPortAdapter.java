package com.bone.claim.infrastructure.client;

import com.bone.claim.application.port.out.InsurerPushGatewayPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 保司报送真实 HTTP 出站适配器（ADR-0021 二期：Outbox 中继的外部投递介质）。
 *
 * <p>与一期进程内事件总线的区别：本适配器把 {@code INSURER_PUSH} 报文经 <b>真实 HTTP POST</b> 打到保司端点，端点由 {@code
 * bone.claim.insurer-push.endpoint} 配置注入（未配置即 {@link #isConfigured()} 为 false，中继回落进程内总线，保持一期行为）。
 * 生产接真实保司时只改配置，不改业务代码。
 *
 * <p><b>报文信封</b>：{@code
 * eventId/eventType/occurredAt/tenantId/aggregate/aggregateId/payload}——外层是轻量信封，{@code payload}
 * 沿用现有 INSURER_PUSH 原始载荷（claimNo/reportNo/policyNo/insurerCode/amount 等），向后兼容。{@code eventId} 用稳定
 * 派生值，保司端可据此幂等去重。
 *
 * <p><b>fail-closed</b>：非 2xx / 超时 / 连接失败一律抛异常，绝不吞错把 Outbox 假置 SENT；由 {@code
 * OutboxApplicationService} 统一累加 retry_count、退避与死信告警。
 */
@Slf4j
@Component
public class InsurerPushGatewayPortAdapter implements InsurerPushGatewayPort {

  // 自建 ObjectMapper：本适配器属基础设施出站端点，切片测试（ApplicationContextRunner 只装指定组件）
  // 不保证注入 ObjectMapper；与 AiProviderProbeAdapter 同款做法，避免无谓的上下文装配依赖。
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient;

  /** 保司报送端点；留空 = 未配置，中继回落进程内事件总线（一期行为）。 */
  @Value("${bone.claim.insurer-push.endpoint:}")
  private String endpoint;

  /** 单次 HTTP 超时。 */
  @Value("${bone.claim.insurer-push.timeout-ms:5000}")
  private long timeoutMs;

  /** 可选 Bearer token，配置后写入 Authorization 头；留空不发。 */
  @Value("${bone.claim.insurer-push.auth-token:}")
  private String authToken;

  public InsurerPushGatewayPortAdapter() {
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(5000)).build();
  }

  /** 是否已配置外部保司端点（供中继选择投递介质）。 */
  @Override
  public boolean isConfigured() {
    return endpoint != null && !endpoint.isBlank();
  }

  @Override
  public String push(InsurerPushMessage message) {
    if (!isConfigured()) {
      throw new IllegalStateException("保司报送端点未配置(bone.claim.insurer-push.endpoint)，拒绝假投递");
    }
    String body = toEnvelope(message);
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Bone-Event-Id", stableEventId(message))
            .header("X-Bone-Event-Type", message.eventType())
            .header("X-Bone-Tenant-Id", String.valueOf(message.tenantId()))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (authToken != null && !authToken.isBlank()) {
      builder.header("Authorization", "Bearer " + authToken);
    }
    try {
      HttpResponse<String> response =
          httpClient.send(
              builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        throw new IllegalStateException(
            "保司报送 HTTP 非 2xx: status=" + status + " body=" + abbreviate(response.body()));
      }
      log.info(
          "保司报送 HTTP 成功: endpoint={} status={} claimId={}",
          endpoint,
          status,
          message.aggregateId());
      return extractReceipt(response.body());
    } catch (java.io.IOException e) {
      throw new IllegalStateException("保司报送 HTTP IO 失败: endpoint=" + endpoint, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("保司报送 HTTP 被中断: endpoint=" + endpoint, e);
    }
  }

  /** 组装报文信封：外层轻量元数据 + 原始 payload（沿用现有保司报送契约）。 */
  private String toEnvelope(InsurerPushMessage message) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("eventId", stableEventId(message));
    envelope.put("eventType", message.eventType());
    envelope.put("occurredAt", Instant.now().toString());
    envelope.put("tenantId", message.tenantId());
    envelope.put("aggregate", message.aggregate());
    envelope.put("aggregateId", message.aggregateId());
    try {
      envelope.put("payload", objectMapper.readValue(message.payloadJson(), Object.class));
    } catch (Exception e) {
      // 载荷本就是 writer 序列化出的合法 JSON；若损坏则原样以字符串带出，不吞掉报送事实。
      envelope.put("payload", message.payloadJson());
      log.warn("保司报送载荷非标准 JSON，按原样带出: claimId={}", message.aggregateId());
    }
    try {
      return objectMapper.writeValueAsString(envelope);
    } catch (Exception e) {
      throw new IllegalStateException("保司报送信封序列化失败", e);
    }
  }

  /** 稳定幂等键：同租户+聚合+事件只产生一个 ID，保司端可据此去重。 */
  static String stableEventId(InsurerPushMessage message) {
    return message.eventType()
        + "-"
        + message.tenantId()
        + "-"
        + message.aggregate()
        + "-"
        + message.aggregateId();
  }

  /** 从保司响应取 receipt 字段；无该字段或非 JSON 时返回空串（不吞投递成功事实）。 */
  private String extractReceipt(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    try {
      JsonNode node = objectMapper.readTree(body);
      JsonNode receipt = node.get("receipt");
      return receipt == null || receipt.isNull() ? "" : receipt.asText();
    } catch (Exception e) {
      log.warn("保司响应非 JSON，receipt 记空: {}", abbreviate(body));
      return "";
    }
  }

  private static String abbreviate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() <= 240 ? s : s.substring(0, 240) + "...";
  }
}
