package com.bone.platform.alert.channel;

import com.bone.platform.alert.AlertChannel;
import com.bone.platform.alert.AlertChannelType;
import com.bone.platform.alert.AlertException;
import com.bone.platform.alert.AlertMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Webhook 通道：将告警 POST 到配置的 webhook URL。 */
@Slf4j
@Service
@ConditionalOnProperty(value = "alert.channels.webhook.enabled", havingValue = "true")
public class WebhookNotificationChannel implements AlertChannel {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String webhookUrl;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  public WebhookNotificationChannel(@Value("${alert.channels.webhook.url:}") String webhookUrl) {
    this.webhookUrl = webhookUrl;
  }

  @Override
  public AlertChannelType channelType() {
    return AlertChannelType.WEBHOOK;
  }

  @Override
  public void send(AlertMessage message) throws AlertException {
    if (webhookUrl == null || webhookUrl.isBlank()) {
      throw new AlertException("Webhook URL 未配置");
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("title", message.getTitle());
      payload.put("level", message.getLevel().name());
      payload.put("content", message.getContent());
      payload.put("timestamp", message.getTimestamp());
      String body = MAPPER.writeValueAsString(payload);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(webhookUrl))
              .timeout(TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new AlertException("Webhook 响应异常: " + response.statusCode());
      }
      log.info("Webhook 已发送: title={}, status={}", message.getTitle(), response.statusCode());
    } catch (AlertException e) {
      throw e;
    } catch (Exception e) {
      throw new AlertException("Webhook 发送失败: " + e.getMessage(), e);
    }
  }
}
