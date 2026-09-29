package com.bone.platform.alert;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertMessage {
  @Builder.Default private String id = UUID.randomUUID().toString();
  private AlertLevel level;
  private String title;
  private String content;
  private String businessId;

  /**
   * 租户 id：由 {@code CompositeAlertService} 在调用线程（已建立租户上下文）捕获一次后随消息下发； 通道可能在异步线程执行，ThreadLocal
   * 不传播，故必须显式携带（N-1 修复核心）。
   */
  private Long tenantId;

  @Builder.Default private Instant timestamp = Instant.now();
  private Map<String, Object> context;

  /** 静态方法，用于快速实例化 AlertMessage */
  public static AlertMessage createAlertMessage(
      AlertLevel level,
      String title,
      String content,
      String businessId,
      Map<String, Object> context) {
    return AlertMessage.builder()
        .level(level)
        .title(title)
        .content(content)
        .businessId(businessId)
        .context(context)
        .build();
  }
}
