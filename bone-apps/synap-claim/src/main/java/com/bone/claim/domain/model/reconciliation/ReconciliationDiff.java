package com.bone.claim.domain.model.reconciliation;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 对账差异明细（append-only，§11.7 / §9.5）。
 *
 * <p>{@code resolution} 默认 {@code PENDING}，人工处置（RESOLVED/ADJUSTED/WAIVED）为 V2（处置写路径未建）。
 * append-only：差异一经落库只追加，不 UPDATE（处置字段的演进留待 V2 以独立处置流水承载）。
 */
@Getter
@Table("t_reconciliation_diff")
public class ReconciliationDiff extends TenantAbstractEntity<Long> {

  public static final String RESOLUTION_PENDING = "PENDING";

  @Column(name = "report_id", nullable = false)
  private Long reportId;

  @Column(name = "claim_no", nullable = false)
  private String claimNo;

  @Column(name = "settlement_no", nullable = false)
  private String settlementNo;

  @Column(name = "diff_type", nullable = false)
  private String diffType;

  @Column(name = "local_amount")
  private BigDecimal localAmount;

  @Column(name = "remote_amount")
  private BigDecimal remoteAmount;

  @Column(name = "local_stage", nullable = false)
  private String localStage;

  @Column(name = "remote_status", nullable = false)
  private String remoteStatus;

  @Column(name = "resolution", nullable = false)
  private String resolution;

  @Column(name = "resolved_by", nullable = false)
  private String resolvedBy;

  ReconciliationDiff() {}

  public static ReconciliationDiff create(
      Long tenantId,
      Long reportId,
      String claimNo,
      String settlementNo,
      DiffType diffType,
      BigDecimal localAmount,
      BigDecimal remoteAmount,
      String localStage,
      String remoteStatus) {
    ReconciliationDiff diff = new ReconciliationDiff();
    diff.setTenantId(tenantId);
    diff.reportId = reportId;
    diff.claimNo = claimNo == null ? "" : claimNo;
    diff.settlementNo = settlementNo == null ? "" : settlementNo;
    diff.diffType = diffType.name();
    diff.localAmount = localAmount;
    diff.remoteAmount = remoteAmount;
    diff.localStage = localStage == null ? "" : localStage;
    diff.remoteStatus = remoteStatus == null ? "" : remoteStatus;
    diff.resolution = RESOLUTION_PENDING;
    diff.resolvedBy = "";
    return diff;
  }
}
