package com.bone.platform.alert.domain.model.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 站内信聚合测试（无容器，纯领域逻辑） */
class NotificationMessageTest {

  @Test
  void create_setsFieldsAndDefaults() {
    NotificationMessage message = NotificationMessage.create(1L, "标题", "内容", "INFO", 42L);

    assertThat(message.getId()).isEqualTo(1L);
    assertThat(message.getTitle()).isEqualTo("标题");
    assertThat(message.getContent()).isEqualTo("内容");
    assertThat(message.getLevel()).isEqualTo("INFO");
    assertThat(message.getUserId()).isEqualTo(42L);
    // 新消息默认未读
    assertThat(message.isRead()).isFalse();
    assertThat(message.getCreatedAt()).isNotNull();
  }

  @Test
  void markRead_flipsReadFlag() {
    NotificationMessage message = NotificationMessage.create(1L, "t", "c", "WARN", 7L);
    assertThat(message.isRead()).isFalse();

    message.markRead();

    assertThat(message.isRead()).isTrue();
  }

  @Test
  void markRead_isIdempotent() {
    NotificationMessage message = NotificationMessage.create(1L, "t", "c", "ERROR", 7L);
    message.markRead();
    message.markRead();
    assertThat(message.isRead()).isTrue();
  }
}
