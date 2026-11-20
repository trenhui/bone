package com.bone.procurement.qualification.domain.model.qualification;

import com.bone.core.annotation.Id;
import com.bone.core.annotation.Version;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 供应商准入单聚合根（INV-12 APPROVED 前不得发 OnboardingApproved；INV-13 一个供应商同时仅一个 IN_REVIEW）。
 *
 * <p>score 为 AI 建议值，仅记录；终决必须人工，AI 不得置 APPROVED。
 */
@Getter
@Setter
@NoArgsConstructor
@Table("t_proc_onboarding")
public class Onboarding extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "supplier_id")
  private String supplierId;

  @Column(name = "onboarding_no")
  private String onboardingNo;

  @Column(name = "background_ref")
  private String backgroundRef;

  @Column(name = "score")
  private BigDecimal score;

  @Column(name = "status")
  private String status;

  @Column(name = "workflow_instance_id")
  private String workflowInstanceId;

  @Column(name = "approved_by")
  private String approvedBy;

  @Version
  @Column(name = "version")
  private Integer version;

  public static Onboarding start(
      Long tenantId, String supplierId, String no, String backgroundRef, BigDecimal aiScore) {
    Onboarding o = new Onboarding();
    o.tenantId = tenantId;
    o.supplierId = supplierId;
    o.onboardingNo = no;
    o.backgroundRef = backgroundRef;
    o.score = aiScore;
    o.status = OnboardingStatus.DRAFT.name();
    o.version = 0;
    return o;
  }

  public OnboardingStatus status() {
    return OnboardingStatus.valueOf(status);
  }

  public void submitForReview() {
    requireTransition(OnboardingStatus.IN_REVIEW);
    this.status = OnboardingStatus.IN_REVIEW.name();
  }

  /** 人工终决入口自 DRAFT 起单时，先走规范迁移到 IN_REVIEW（不跳过中间态）。 */
  public void submitForReviewIfDraft() {
    if (status() == OnboardingStatus.DRAFT) {
      submitForReview();
    }
  }

  /** 唯一可置 APPROVED 的入口：operator 取登录态，AI 建议评分不得直接终决。 */
  public void approveByHuman(String operator) {
    if (operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("准入终决必须具名人工操作人，AI/匿名不得终决");
    }
    requireTransition(OnboardingStatus.APPROVED);
    this.status = OnboardingStatus.APPROVED.name();
    this.approvedBy = operator;
  }

  public void rejectByHuman(String operator) {
    if (operator == null || operator.isBlank()) {
      throw new IllegalArgumentException("准入终决必须具名人工操作人");
    }
    requireTransition(OnboardingStatus.REJECTED);
    this.status = OnboardingStatus.REJECTED.name();
    this.approvedBy = operator;
  }

  private void requireTransition(OnboardingStatus target) {
    if (!status().canTransitionTo(target)) {
      throw new IllegalStateException("非法准入状态迁移 " + status + " -> " + target);
    }
  }
}
