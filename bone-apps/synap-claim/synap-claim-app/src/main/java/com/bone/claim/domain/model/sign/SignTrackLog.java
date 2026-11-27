package com.bone.claim.domain.model.sign;

import com.bone.claim.domain.model.sign.valueobject.SignStatus;
import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 签收轨迹（append-only，无 version 列）。 */
@Getter
@NoArgsConstructor
@Table("t_claim_sign_track")
public class SignTrackLog extends TenantAbstractEntity<Long> {

  @Column(name = "batch_id")
  private Long batchId;

  @Column(name = "claim_id")
  private Long claimId;

  @Column(name = "sign_status")
  private String signStatus;

  @Column(name = "action_type")
  private String actionType;

  @Column(name = "message")
  private String message;

  @Column(name = "remark")
  private String remark;

  @Column(name = "operator")
  private String operator;

  @Column(name = "occurred_at")
  private Instant occurredAt;

  public static SignTrackLog of(
      long tenantId,
      long batchId,
      Long claimId,
      SignStatus status,
      String actionType,
      String message,
      String remark,
      String operator) {
    SignTrackLog log = new SignTrackLog();
    log.setTenantId(tenantId);
    log.batchId = batchId;
    log.claimId = claimId;
    log.signStatus = status == null ? null : status.code();
    log.actionType = actionType;
    log.message = message;
    log.remark = remark;
    log.operator = operator;
    log.occurredAt = Instant.now();
    return log;
  }
}
