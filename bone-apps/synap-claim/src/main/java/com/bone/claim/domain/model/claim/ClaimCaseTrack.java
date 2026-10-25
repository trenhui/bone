package com.bone.claim.domain.model.claim;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;

/**
 * 案件状态流转流水（ADR-003 事件溯源）。
 *
 * <p>append-only：仅由 {@code ClaimStatusChangedEvent} 的订阅者 {@code ClaimCaseTrackRecorder}
 * 写入，记录每一次合法状态跃迁 （§6.2 状态机）。{@code created_at} 由 {@link TenantAbstractEntity} 审计列注入，即「发生时间」，不另设业务列。
 *
 * <p>本表是 {@code claim_case_sla} 物化读模型（SLA 计时聚合，ADR-003 / §11.7 由调度任务按租户物化）的来源，亦支撑按租户审计与对账。
 */
@Getter
@Table("t_claim_case_track")
public class ClaimCaseTrack extends TenantAbstractEntity<Long> {

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "from_status", nullable = false)
  private String fromStatus;

  @Column(name = "to_status", nullable = false)
  private String toStatus;

  @Column(name = "stage", nullable = false)
  private String stage;

  @Column(name = "action_code", nullable = false)
  private String actionCode;

  @Column(name = "operator_id", nullable = false)
  private String operatorId;

  @Column(name = "operator_name")
  private String operatorName;

  @Column(name = "remark")
  private String remark;

  ClaimCaseTrack() {}

  private ClaimCaseTrack(
      Long tenantId,
      Long claimId,
      String fromStatus,
      String toStatus,
      String stage,
      String actionCode,
      String operatorId,
      String operatorName,
      String remark) {
    setTenantId(tenantId);
    this.claimId = claimId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.stage = stage;
    this.actionCode = actionCode;
    this.operatorId = operatorId;
    this.operatorName = operatorName;
    this.remark = remark;
  }

  public static ClaimCaseTrack of(
      Long tenantId,
      Long claimId,
      String fromStatus,
      String toStatus,
      String stage,
      String actionCode,
      String operatorId,
      String operatorName,
      String remark) {
    return new ClaimCaseTrack(
        tenantId,
        claimId,
        fromStatus,
        toStatus,
        stage,
        actionCode,
        operatorId,
        operatorName,
        remark);
  }
}
