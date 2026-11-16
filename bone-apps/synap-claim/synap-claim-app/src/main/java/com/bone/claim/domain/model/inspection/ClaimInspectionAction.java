package com.bone.claim.domain.model.inspection;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;

/**
 * 抽检处置留痕（append-only 事实，不可改写）。
 *
 * <p>抽检人是认证主体，不接受客户端声明；结论 PASS/REJECT/BACK 三值。已支付案件打回必须先冲正， reversal_verified
 * 记录冲正前置是否已核验；feature_enabled 留痕处置时 INSPECTION_ACTION 开关态， 供「开关关闭只登记不流转」自证。不设 {@code @Version}、不写
 * UPDATE：事实一旦落行不可改写。
 */
@Getter
@Table("t_claim_inspection_action")
public class ClaimInspectionAction extends TenantAbstractEntity<Long> {

  public static final String DECISION_PASS = "PASS";
  public static final String DECISION_REJECT = "REJECT";
  public static final String DECISION_BACK = "BACK";

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "task_id")
  private Long taskId;

  @Column(name = "inspector_id", nullable = false)
  private String inspectorId;

  @Column(name = "decision", nullable = false)
  private String decision;

  @Column(name = "back_reason")
  private String backReason;

  @Column(name = "reversal_verified", nullable = false)
  private Integer reversalVerified;

  @Column(name = "feature_enabled", nullable = false)
  private Integer featureEnabled;

  ClaimInspectionAction() {}

  /** 只登记（开关关闭或无需重办）：taskId 为空。 */
  public static ClaimInspectionAction record(
      Long id,
      Long tenantId,
      long claimId,
      String inspectorId,
      String decision,
      String backReason,
      boolean reversalVerified,
      boolean featureEnabled,
      Date now) {
    ClaimInspectionAction e = new ClaimInspectionAction();
    e.setId(id);
    e.setTenantId(tenantId);
    e.claimId = claimId;
    e.inspectorId = inspectorId == null ? "" : inspectorId;
    e.decision = decision;
    e.backReason = backReason == null ? "" : backReason;
    e.reversalVerified = reversalVerified ? 1 : 0;
    e.featureEnabled = featureEnabled ? 1 : 0;
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    return e;
  }

  /** 打回且开关开启：关联生成的重办任务（append-only 事实二次落行时用关联补齐）。 */
  public void linkTask(long taskId) {
    this.taskId = taskId;
  }
}
