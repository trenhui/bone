package com.bone.claim.domain.model.inspection;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;

/**
 * 任务转交留痕（append-only 事实，历史不可更新）。
 *
 * <p>不设 {@code @Version}、不提供改字段方法：转出/转入/原因是责任证据，只能追加。
 */
@Getter
@Table("t_claim_task_transfer")
public class ClaimTaskTransfer extends TenantAbstractEntity<Long> {

  @Column(name = "task_id", nullable = false)
  private Long taskId;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "from_assignee_id")
  private String fromAssigneeId;

  @Column(name = "to_assignee_id", nullable = false)
  private String toAssigneeId;

  @Column(name = "reason")
  private String reason;

  @Column(name = "transferred_by", nullable = false)
  private String transferredBy;

  ClaimTaskTransfer() {}

  public static ClaimTaskTransfer record(
      Long id,
      Long tenantId,
      long taskId,
      long claimId,
      String fromAssigneeId,
      String toAssigneeId,
      String reason,
      String transferredBy,
      Date now) {
    if (toAssigneeId == null || toAssigneeId.isBlank()) {
      throw new IllegalArgumentException("转入人不能为空");
    }
    if (transferredBy == null || transferredBy.isBlank()) {
      throw new IllegalArgumentException("操作人不能为空（必须取认证主体）");
    }
    ClaimTaskTransfer e = new ClaimTaskTransfer();
    e.setId(id);
    e.setTenantId(tenantId);
    e.taskId = taskId;
    e.claimId = claimId;
    e.fromAssigneeId = fromAssigneeId == null ? "" : fromAssigneeId;
    e.toAssigneeId = toAssigneeId;
    e.reason = reason == null ? "" : reason;
    e.transferredBy = transferredBy;
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    return e;
  }
}
