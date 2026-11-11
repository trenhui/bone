package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P0-3：快照 TTL 配置化，默认 30 天 ≥ 理赔争议窗口（诊断报告 §14.1 P0-3 验收判据）。 */
class ClaimIdempotencyPropertiesTest {

  @Test
  @DisplayName("默认 TTL 必须为 30 天（写死 24h 的旧口径已移除）")
  void defaultTtlCoversDisputeWindow() {
    assertThat(new ClaimIdempotencyProperties().getTtl()).isEqualTo(Duration.ofDays(30));
  }

  @Test
  @DisplayName("TTL 可经配置覆盖（bone.claim.idempotency.ttl）")
  void ttlIsConfigurable() {
    ClaimIdempotencyProperties props = new ClaimIdempotencyProperties();
    props.setTtl(Duration.ofDays(45));
    assertThat(props.getTtl()).isEqualTo(Duration.ofDays(45));
  }
}
