package com.bone.ainotification.infrastructure.notification;

import com.bone.ainotification.domain.gateway.NotificationPort;
import com.bone.ainotification.domain.model.AlertEvent;
import com.bone.ainotification.domain.model.NotificationChannel;
import com.bone.ainotification.domain.model.NotificationResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Webhook 通知适配器（零新依赖，出站 HTTP 用 JDK {@link HttpClient}，对齐 bone-integration RestClientImpl）。
 *
 * <p>{@code target} 为 Webhook URL；未配置（且未设默认）时返回「未投递」结果而非抛错，保证上下文可启动。
 */
public class WebhookNotificationAdapter implements NotificationPort {

  private static final Logger log = LoggerFactory.getLogger(WebhookNotificationAdapter.class);
  private final HttpClient httpClient;
  private final String defaultWebhookUrl;

  public WebhookNotificationAdapter(HttpClient httpClient, String defaultWebhookUrl) {
    this.httpClient = httpClient;
    this.defaultWebhookUrl = defaultWebhookUrl == null ? "" : defaultWebhookUrl;
  }

  @Override
  public NotificationResult dispatch(AlertEvent event, NotificationChannel channel, String target) {
    String url = (target == null || target.isBlank()) ? defaultWebhookUrl : target;
    if (url.isBlank()) {
      return new NotificationResult(channel, false, null, "webhook url not configured");
    }
    try {
      HttpRequest request = buildRequest(event, channel, url);
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
      return new NotificationResult(
          channel, ok, ok ? response.body() : null, ok ? null : "http " + response.statusCode());
    } catch (Exception e) {
      log.warn("Webhook 通知外发失败 alertId={}", event.alertId(), e);
      return new NotificationResult(channel, false, null, e.getMessage());
    }
  }

  HttpRequest buildRequest(AlertEvent event, NotificationChannel channel, String url) {
    return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(toJson(event, channel), StandardCharsets.UTF_8))
        .build();
  }

  String toJson(AlertEvent event, NotificationChannel channel) {
    return "{"
        + "\"alertId\":\""
        + jsonEsc(event.alertId())
        + "\""
        + ",\"tenantId\":"
        + event.tenantId()
        + ",\"severity\":\""
        + jsonEsc(event.severity())
        + "\""
        + ",\"channel\":\""
        + channel
        + "\""
        + ",\"title\":\""
        + jsonEsc(event.title())
        + "\""
        + ",\"body\":\""
        + jsonEsc(event.body())
        + "\""
        + ",\"sourceModule\":\""
        + jsonEsc(event.sourceModule())
        + "\""
        + ",\"occurredAt\":\""
        + event.occurredAt()
        + "\"}";
  }

  private static String jsonEsc(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }
}
