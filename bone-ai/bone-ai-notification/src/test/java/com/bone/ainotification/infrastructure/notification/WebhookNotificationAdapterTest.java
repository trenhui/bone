package com.bone.ainotification.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ainotification.domain.model.AlertEvent;
import com.bone.ainotification.domain.model.NotificationChannel;
import com.bone.ainotification.domain.model.NotificationResult;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookNotificationAdapterTest {

  private final WebhookNotificationAdapter adapter =
      new WebhookNotificationAdapter(HttpClient.newHttpClient(), "https://default");

  @Test
  void builds_post_request_to_target() {
    AlertEvent event = new AlertEvent("A1", 42L, "HIGH", "t", "b", "m", Instant.now());
    HttpRequest req = adapter.buildRequest(event, NotificationChannel.WEBHOOK, "https://hook");

    assertThat(req.method()).isEqualTo("POST");
    assertThat(req.uri().toString()).isEqualTo("https://hook");
    assertThat(req.headers().firstValue("Content-Type").orElse("")).contains("application/json");
  }

  @Test
  void json_body_carries_event_fields_and_escapes() {
    AlertEvent event =
        new AlertEvent(
            "A1",
            42L,
            "HIGH",
            "ti\"tle",
            "b\nody",
            "bone-ai-rules",
            Instant.parse("2026-10-16T00:00:00Z"));
    String json = adapter.toJson(event, NotificationChannel.WEBHOOK);

    assertThat(json)
        .contains("\"alertId\":\"A1\"")
        .contains("\"tenantId\":42")
        .contains("\"channel\":\"WEBHOOK\"")
        .contains("ti\\\"tle")
        .contains("b\\nody");
  }

  @Test
  void missing_url_returns_undelivered() {
    WebhookNotificationAdapter noUrl =
        new WebhookNotificationAdapter(HttpClient.newHttpClient(), "");
    NotificationResult r =
        noUrl.dispatch(
            new AlertEvent("A1", 42L, "HIGH", "t", "b", "m", Instant.now()),
            NotificationChannel.WEBHOOK,
            "");
    assertThat(r.delivered()).isFalse();
    assertThat(r.error()).contains("webhook url not configured");
  }
}
