package com.bone.workflow.starter.sdk;

import com.bone.core.domain.entity.Entity;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@code wf_status_outbox}（状态投影发件箱）的 SDK 映射行类。
 *
 * <p>可变行类（{@code SmartRowMapper} 反射回填）；字段与 {@link WfStatusOutboxSdkRepository} 各 SELECT 的列清单
 * 逐列对应，{@code id} 继承自 {@link Entity}。
 *
 * <p><b>两个查询的用途不同，列清单随之不同</b>：{@code pollDueStatusIntents} 取的是发件箱行（含 {@code
 * tenant_id/state/retry_count/next_retry_at}）；{@code findTerminalMissingProjection} 取的是 {@code
 * wf_instance} 行（只映射 {@code id/entity_code/record_id/intent=phase}）——对账补投意图的 {@code instanceId} 由
 * {@code WorkflowStatusOutboxRepository#toReconcileIntent} 以实例 id 双填、 {@code actionCode} 由 phase
 * 派生，不依赖本行类的其余字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfStatusOutboxRow extends Entity<String> {

  private Long tenantId;
  private String instanceId;
  private String entityCode;
  private String recordId;
  private String intent;
  private String actionCode;
  private String paramsJson;
  private String state;
  private int retryCount;
  private Instant nextRetryAt;
  private Instant createdAt;
}
