package com.bone.masterdata.domain.model.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.masterdata.domain.model.dispatch.OutboxDelivery.State;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 投递状态机纯单测：PENDING → DELIVERED / DEAD，失败有界且原因可查。 */
class OutboxDeliveryTest {

  private static final Instant T0 = Instant.parse("2026-11-13T02:00:00Z");

  private static MasterDataRecordDispatchedEvent event(long version) {
    return MasterDataRecordDispatchedEvent.of(
        "E-" + version, 1L, "P-1", version, "GOLDEN_RECORD_MERGED", 1, T0, "a".repeat(64));
  }

  @Test
  @DisplayName("成功投递记录一次尝试，且不可重复标记")
  void deliveredOnce() {
    OutboxDelivery d = OutboxDelivery.pending(event(1), T0);
    assertThat(d.getAttempts()).isZero();
    OutboxDelivery delivered = d.delivered("DELIVERED", T0);
    assertThat(delivered.getState()).isEqualTo(State.DELIVERED);
    assertThat(delivered.getAttempts()).isEqualTo(1);
    assertThat(delivered.getDeliveryNote()).isEqualTo("DELIVERED");
    assertThatThrownBy(() -> delivered.delivered("AGAIN", T0)).hasMessageContaining("仅 PENDING");
  }

  @Test
  @DisplayName("未达上限保持 PENDING 供重试，原因留痕")
  void boundedRetryKeepsPending() {
    OutboxDelivery d = OutboxDelivery.pending(event(1), T0).failed("消费侧超时", 3, T0);
    assertThat(d.isPending()).isTrue();
    assertThat(d.getAttempts()).isEqualTo(1);
    assertThat(d.getLastError()).contains("消费侧超时");
  }

  @Test
  @DisplayName("达到上限转 DEAD，事件与原因仍可查（不静默丢弃）")
  void deadLetterRetainsEventAndReason() {
    OutboxDelivery d = OutboxDelivery.pending(event(1), T0).failed("第一次", 2, T0);
    OutboxDelivery dead = d.failed("第二次", 2, T0);
    assertThat(dead.isDead()).isTrue();
    assertThat(dead.getAttempts()).isEqualTo(2);
    assertThat(dead.getEvent().idempotencyKey()).isEqualTo("1|P-1|1|GOLDEN_RECORD_MERGED");
    assertThat(dead.getLastError()).contains("第二次");
    assertThatThrownBy(() -> dead.failed("第三次", 2, T0)).hasMessageContaining("仅 PENDING");
  }

  @Test
  @DisplayName("非法参数 fail-closed")
  void invalidArguments() {
    assertThatThrownBy(() -> OutboxDelivery.pending(null, T0)).hasMessageContaining("缺少事件");
    assertThatThrownBy(() -> OutboxDelivery.pending(event(1), T0).failed("x", 0, T0))
        .hasMessageContaining("最大重试次数必须为正数");
    assertThatThrownBy(
            () ->
                MasterDataRecordDispatchedEvent.of("E", 1L, "P-1", 0L, "T", 1, T0, "a".repeat(64)))
        .hasMessageContaining("版本必须为正数");
  }
}
