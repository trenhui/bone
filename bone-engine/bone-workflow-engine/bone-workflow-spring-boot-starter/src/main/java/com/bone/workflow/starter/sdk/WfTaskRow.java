package com.bone.workflow.starter.sdk;

import com.bone.core.domain.entity.Entity;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@code wf_task}（审批任务）的 SDK 映射行类。
 *
 * <p>可变行类（{@code SmartRowMapper} 反射回填，record 的 final 字段不可赋值）；字段与 {@link WfTaskSdkRepository} 各
 * SELECT 的列清单逐列对应，{@code id} 继承自 {@link Entity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfTaskRow extends Entity<String> {

  private String instanceId;
  private String nodeCode;
  private String assigneeId;
  private String status;
  private String decision;
  private String comment;
  private Instant dueAt;
  private Instant createdAt;
  private Instant decidedAt;
}
