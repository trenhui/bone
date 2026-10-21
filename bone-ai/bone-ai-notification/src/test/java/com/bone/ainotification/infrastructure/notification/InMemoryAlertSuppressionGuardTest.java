package com.bone.ainotification.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.ainotification.domain.model.AlertEvent;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryAlertSuppressionGuardTest {

  private final InMemoryAlertSuppressionGuard guard =
      new InMemoryAlertSuppressionGuard(Duration.ofMinutes(5), 2);
  private final Instant t0 = Instant.parse("2026-10-20T00:00:00Z");

  private AlertEvent event(String id) {
    return new AlertEvent(id, 42L, "HIGH", "风控告警", "body", "bone-ai-rules", t0);
  }

  @Test
  void allows_first_n_then_suppresses_excess() {
    assertThat(guard.shouldSuppress(event("a1"), t0)).isFalse();
    assertThat(guard.shouldSuppress(event("a2"), t0)).isFalse();
    assertThat(guard.shouldSuppress(event("a3"), t0)).isTrue(); // 超额静默
  }

  @Test
  void different_keys_are_independent() {
    guard.shouldSuppress(event("a1"), t0);
    guard.shouldSuppress(event("a2"), t0); // 同类键已满
    AlertEvent other = new AlertEvent("b1", 42L, "HIGH", "另一个告警", "body", "bone-ai-rules", t0);
    assertThat(guard.shouldSuppress(other, t0)).isFalse(); // 不同键独立计数
  }

  @Test
  void window_rollover_resets_counter() {
    guard.shouldSuppress(event("a1"), t0);
    guard.shouldSuppress(event("a2"), t0);
    assertThat(guard.shouldSuppress(event("a3"), t0)).isTrue();
    // 窗口过后滚动到新窗口，重新放行
    Instant later = t0.plus(Duration.ofMinutes(6));
    assertThat(guard.shouldSuppress(event("a4"), later)).isFalse();
  }

  @Test
  void key_ignores_body_and_alert_id() {
    // 同租户/模块/严重度/标题、不同 alertId 与 body → 同类，参与同一计数
    AlertEvent e1 = new AlertEvent("x1", 42L, "HIGH", "风控告警", "body-1", "bone-ai-rules", t0);
    AlertEvent e2 = new AlertEvent("x2", 42L, "HIGH", "风控告警", "body-2", "bone-ai-rules", t0);
    assertThat(guard.shouldSuppress(e1, t0)).isFalse();
    assertThat(guard.shouldSuppress(e2, t0)).isFalse();
    assertThat(guard.shouldSuppress(event("x3"), t0)).isTrue();
  }

  @Test
  void rejects_invalid_parameters() {
    assertThatThrownBy(() -> new InMemoryAlertSuppressionGuard(Duration.ZERO, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InMemoryAlertSuppressionGuard(Duration.ofMinutes(1), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
