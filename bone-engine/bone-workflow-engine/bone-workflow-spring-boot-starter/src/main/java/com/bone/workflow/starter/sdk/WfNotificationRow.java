package com.bone.workflow.starter.sdk;

import com.bone.core.domain.entity.Entity;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@code wf_notification}（站内信通知）的 SDK 映射行类。
 *
 * <p>字段与 {@link WfNotificationSdkRepository} 各 SELECT 的列清单逐列对应；{@code id} 继承自 {@link Entity}。
 *
 * <p><b>为什么必须是可变类而不是 record</b>：SDK 的 {@code SmartRowMapper} 用反射 {@code field.set} 回填列值， record 的
 * final 字段无法赋值——行类只承载「查询结果 → 适配器」的原始数据，领域转换由适配器负责。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfNotificationRow extends Entity<String> {

  private Long tenantId;
  private String recipientId;
  private String type;
  private String instanceId;
  private String entityCode;
  private String recordId;
  private String nodeCode;
  private String title;
  private String body;
  private int readFlag;
  private String deliveryState;
  private Instant createdAt;
  private Instant updatedAt;
}
