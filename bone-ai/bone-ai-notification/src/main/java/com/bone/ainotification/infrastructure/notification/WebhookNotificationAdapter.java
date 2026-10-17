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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Webhook 通知适配器（零新依赖，出站 HTTP 用 JDK {@link HttpClient}，对齐 bone-integration RestClientImpl）。
 *
 * <p>{@code target} 为 Webhook URL；未配置（且未设默认）时返回「未投递」结果而非抛错，保证上下文可启动。
 *
 * <p><b>出站安全加固（2026-10-17 复核修复）</b>：
 *
 * <ul>
 *   <li>主机白名单：仅放行允许后缀的 host，拒绝内网/云元数据地址（SSRF 防护，{@code target} 是不可信输入）；
 *   <li>HMAC 签名：配置签名密钥后对请求体做 HMAC-SHA256，置于 {@code X-Bone-Signature} 头，接收方据以验真；
 *   <li>失败重试：网络/非 2xx 失败按指数退避重试（最多 3 次），避免瞬时抖动导致漏警。
 * </ul>
 */
public class WebhookNotificationAdapter implements NotificationPort {

  private static final Logger log = LoggerFactory.getLogger(WebhookNotificationAdapter.class);
  private static final int MAX_ATTEMPTS = 3;
  private static final long BASE_BACKOFF_MS = 200;

  private final HttpClient httpClient;
  private final String defaultWebhookUrl;
  private final String signingSecret;
  private final List<String> allowedHosts;

  public WebhookNotificationAdapter(
      HttpClient httpClient,
      String defaultWebhookUrl,
      String signingSecret,
      List<String> allowedHosts) {
    this.httpClient = httpClient;
    this.defaultWebhookUrl = defaultWebhookUrl == null ? "" : defaultWebhookUrl;
    this.signingSecret = signingSecret;
    this.allowedHosts = allowedHosts == null ? List.of() : allowedHosts;
  }

  @Override
  public NotificationResult dispatch(AlertEvent event, NotificationChannel channel, String target) {
    String url = (target == null || target.isBlank()) ? defaultWebhookUrl : target;
    if (url.isBlank()) {
      return new NotificationResult(channel, false, null, "webhook url not configured");
    }
    // SSRF 防护：出站前校验 host（白名单 + 拒绝内网/元数据）
    guardHost(url);
    String body = toJson(event, channel);
    String signature = WebhookSigner.sign(body, signingSecret);

    Throwable lastErr = null;
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      try {
        HttpRequest request = buildRequest(event, channel, url, body, signature);
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
        if (ok) {
          return new NotificationResult(channel, true, response.body(), null);
        }
        // 4xx 不重试（配置/鉴权错误），仅 5xx/网络重试
        if (response.statusCode() >= 400 && response.statusCode() < 500) {
          return new NotificationResult(channel, false, null, "http " + response.statusCode());
        }
        lastErr = new RuntimeException("http " + response.statusCode());
      } catch (Exception e) {
        lastErr = e;
      }
      if (attempt < MAX_ATTEMPTS - 1) {
        try {
          Thread.sleep(BASE_BACKOFF_MS * (1L << attempt));
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    log.warn("Webhook 通知外发失败(重试耗尽) alertId={}", event.alertId(), lastErr);
    return new NotificationResult(
        channel, false, null, lastErr == null ? "unknown" : lastErr.getMessage());
  }

  HttpRequest buildRequest(
      AlertEvent event, NotificationChannel channel, String url, String body, String signature) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    if (signature != null && !signature.isBlank()) {
      builder.header("X-Bone-Signature", signature);
    }
    return builder.build();
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

  /** SSRF：仅放行白名单后缀的 host，并拒绝内网/云元数据保留地址。 */
  private void guardHost(String url) {
    URI parsed = URI.create(url);
    String scheme = parsed.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new IllegalArgumentException("webhook 仅允许 http/https: " + url);
    }
    String host = parsed.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("webhook 缺少 host: " + url);
    }
    String h = host.toLowerCase();
    if (h.equals("localhost")
        || h.endsWith(".localhost")
        || h.equals("0.0.0.0")
        || h.equals("169.254.169.254")
        || h.endsWith(".internal")
        || h.endsWith(".svc")
        || h.endsWith(".cluster.local")) {
      throw new IllegalArgumentException("webhook 命中内网/元数据保留地址，拒绝出站: " + url);
    }
    if (!allowedHosts.isEmpty()
        && allowedHosts.stream()
            .noneMatch(suffix -> h.equals(suffix) || h.endsWith("." + suffix))) {
      throw new IllegalArgumentException("webhook host 不在白名单: " + url);
    }
  }

  private static String jsonEsc(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }
}
