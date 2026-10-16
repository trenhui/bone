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
    return HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json")
        .POST(
            HttpRequest.BodyPublishers.ofString(
                call.payload() == null ? "" : call.payload(), StandardCharsets.UTF_8))
        .build();
  }
}
