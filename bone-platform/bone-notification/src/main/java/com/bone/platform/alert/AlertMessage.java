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
  @Builder.Default private Instant timestamp = Instant.now();
  private Map<String, Object> context;

  /**
   * 静态方法，用于快速实例化 AlertMessage
   *
   * @param level AlertMessage 的级别
   * @param title 标题
   * @param content 内容
   * @param businessId 业务ID
   * @param context 附加的上下文信息
   * @return AlertMessage 实例
   */
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
