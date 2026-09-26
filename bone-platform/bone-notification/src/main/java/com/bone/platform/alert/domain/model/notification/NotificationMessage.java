package com.bone.platform.alert.domain.model.notification;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 站内信聚合（按租户隔离：用户 PII，R9 多租户数据隔离方案）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("ntf_message")
public class NotificationMessage extends TenantAggregateRoot<Long> {
  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String title;
  private String content;
  private String level;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "is_read")
  private boolean read;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public static NotificationMessage create(
      Long id, Long tenantId, String title, String content, String level, Long userId) {
    NotificationMessage message = new NotificationMessage();
    message.id = id;
    message.setTenantId(tenantId);
    message.title = title;
    message.content = content;
    message.level = level;
    message.userId = userId;
    message.read = false;
    message.createdAt = LocalDateTime.now();
    return message;
  }

  public void markRead() {
    this.read = true;
  }
}
