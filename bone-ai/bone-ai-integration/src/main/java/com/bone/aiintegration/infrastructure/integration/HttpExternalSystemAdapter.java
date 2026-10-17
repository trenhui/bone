package com.bone.aiintegration.infrastructure.integration;

import com.bone.aiintegration.domain.gateway.ExternalSystemPort;
import com.bone.aiintegration.domain.model.ExternalCall;
import com.bone.aiintegration.domain.model.ExternalCallOutcome;
import com.bone.aiintegration.domain.model.IntegrationMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 外部系统 HTTP 适配器（零新依赖，出站用 JDK {@link HttpClient}，对齐 bone-integration RestClientImpl）。
 *
 * <p>Shadow 模式在 URI 追加 {@code dryRun=true}，由外部系统按 dry-run 口径执行（不落地副作用）。
 */
public class HttpExternalSystemAdapter implements ExternalSystemPort {

  private static final Logger log = LoggerFactory.getLogger(HttpExternalSystemAdapter.class);
  private final HttpClient httpClient;

  public HttpExternalSystemAdapter(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public ExternalCallOutcome call(ExternalCall call) {
    String uri = resolveUri(call);
    // SSRF 防护：解析 + 方案校验在发请求前（拒绝非 http(s) / 内网与云元数据地址）
    guardAgainstSsrf(uri);
    Instant start = Instant.now();
    try {
      HttpRequest request = buildRequest(call, uri);
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      long latencyMs = Duration.between(start, Instant.now()).toMillis();
      return new ExternalCallOutcome(
          call.callId(), call.systemId(), response.statusCode(), response.body(), latencyMs, null);
    } catch (Exception e) {
      long latencyMs = Duration.between(start, Instant.now()).toMillis();
      log.warn("外部系统调用失败 systemId={} endpoint={}", call.systemId(), call.endpoint(), e);
      return new ExternalCallOutcome(
          call.callId(), call.systemId(), 0, null, latencyMs, e.getMessage());
    }
  }

  String resolveUri(ExternalCall call) {
    String endpoint = call.endpoint();
    if (call.mode() == IntegrationMode.SHADOW) {
      return endpoint.contains("?") ? endpoint + "&dryRun=true" : endpoint + "?dryRun=true";
    }
    return endpoint;
  }

  HttpRequest buildRequest(ExternalCall call, String uri) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            // 幂等三件套：callId 即幂等键，外部系统可据以去重重放
            .header("Idempotency-Key", call.callId())
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    call.payload() == null ? "" : call.payload(), StandardCharsets.UTF_8));
    return builder.build();
  }

  /**
   * SSRF 防护：仅放行 http/https，拒绝对内网保留地址与云元数据（169.254.169.254）的访问。
   *
   * <p>endpoint 由调用方（编排节点）传入，属不可信输入，必须在出站前拦一道，否则可借集成节点打内网。
   */
  private void guardAgainstSsrf(String uri) {
    URI parsed;
    try {
      parsed = URI.create(uri);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("集成 endpoint 非法 URI: " + uri, e);
    }
    String scheme = parsed.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new IllegalArgumentException("集成 endpoint 仅允许 http/https: " + uri);
    }
    String host = parsed.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("集成 endpoint 缺少 host: " + uri);
    }
    String h = host.toLowerCase();
    if (h.equals("localhost")
        || h.endsWith(".localhost")
        || h.equals("0.0.0.0")
        || h.endsWith(".internal")
        || h.endsWith(".svc")
        || h.endsWith(".cluster.local")
        || h.equals("169.254.169.254")) {
      throw new IllegalArgumentException("集成 endpoint 命中内网/元数据保留地址，拒绝出站: " + uri);
    }
  }
}
