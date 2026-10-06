package com.bone.blueprint.domain.model.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 渠道聚合纯单测（R8：无容器、直接验证领域不变量）。 */
class ChannelTest {

  @Test
  void registerFillsDisplayNameAndDefaults() {
    Channel channel = Channel.register(1L, 0L, "TAOBAO", "https://eco.taobao.com", "k");

    assertEquals("TAOBAO", channel.getChannelCode());
    assertEquals("淘宝", channel.getChannelName());
    assertTrue(channel.isEnabled(), "新注册渠道默认启用");
    assertFalse(channel.getOrderSyncEnabled(), "订单同步默认关闭，避免开通即拉单");
  }

  @Test
  void registerRejectsUnsupportedChannel() {
    assertThrows(
        IllegalArgumentException.class, () -> Channel.register(2L, 0L, "AMAZON", null, null));
  }

  @Test
  void disableAlsoTurnsOffOrderSync() {
    Channel channel = Channel.register(3L, 0L, "JD", null, null);
    channel.enableOrderSync();
    assertTrue(channel.getOrderSyncEnabled());

    channel.disable();

    assertFalse(channel.isEnabled());
    assertFalse(channel.getOrderSyncEnabled(), "停用后必须关闭同步，否则定时任务仍在拉单");
  }

  @Test
  void enableSyncOnDisabledChannelIsRejected() {
    Channel channel = Channel.register(4L, 0L, "PDD", null, null);
    channel.disable();

    assertThrows(IllegalStateException.class, channel::enableOrderSync);
  }

  @Test
  void disableOrderSyncDoesNotRequireEnabledChannel() {
    Channel channel = Channel.register(41L, 0L, "PDD", null, null);

    channel.enableOrderSync();
    channel.disableOrderSync();

    assertFalse(channel.getOrderSyncEnabled(), "关闭同步无前置约束，停用渠道也必须能关");
  }

  @Test
  void markSyncedAndRoutedImplRecordObservability() {
    Channel channel = Channel.register(5L, 0L, "DOUYIN", null, null);

    channel.markRoutedImpl("DY_CHANNEL_PRODUCT_EXT");
    channel.markSynced(java.time.Instant.now());

    assertEquals("DY_CHANNEL_PRODUCT_EXT", channel.getExtImplCode());
    assertNotNull(channel.getLastSyncAt());
  }
}
