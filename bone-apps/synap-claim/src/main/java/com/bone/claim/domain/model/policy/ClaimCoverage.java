package com.bone.claim.domain.model.policy;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 保障项/险种聚合根（{@code t_claim_coverage}）——参考实现三层树的第二层。
 *
 * <p>层级：{@code 保单 → 计划 → 险种 → 责任}。责任通过 {@code t_adj_liability.coverage_id} 指向险种，
 * 因此"责任配置页"能按计划/险种分组展示，而不是一堆无序的责任行。
 *
 * <p><b>险种额度与责任额度的关系</b>：险种额度是<b>这一整类责任共享的上限</b>，责任额度是<b>单条责任的上限</b>；
 * 理算时实际可赔额取各层剩余额的最小值（参考实现的"多维控额"）。本仓一期把两者都配置进来， 但引擎的取 min 逻辑尚未接通——<b>这是已知的"配了暂时不生效"项</b>，故本类的
 * javadoc 明确标注， 不让运营误以为配了就一定按此赔（"配了不生效"是本仓的头号正确性债务）。
 *
 * <p><b>不变量</b>：险种编码非空；额度不能为负；责任形式取三值之一。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_coverage")
public class ClaimCoverage extends TenantAggregateRoot<Long> {

  /** 责任形式：费用补偿型。 */
  public static final String LIABILITY_REIMBURSEMENT = "REIMBURSEMENT";

  /** 责任形式：定额给付型。 */
  public static final String LIABILITY_FIXED_AMOUNT = "FIXED_AMOUNT";

  /** 责任形式：津贴给付型。 */
  public static final String LIABILITY_ALLOWANCE = "ALLOWANCE";

  @Column(name = "plan_id", nullable = false)
  private Long planId;

  @Column(name = "coverage_code", nullable = false)
  private String coverageCode;

  @Column(name = "coverage_name")
  private String coverageName;

  @Column(name = "coverage_limit", nullable = false)
  private BigDecimal coverageLimit;

  @Column(name = "liability_type", nullable = false)
  private String liabilityType;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  private ClaimCoverage(
      Long id,
      Long tenantId,
      Long planId,
      String coverageCode,
      String coverageName,
      BigDecimal coverageLimit,
      String liabilityType,
      String status,
      Instant now) {
    setId(id);
    setTenantId(tenantId);
    this.planId = planId;
    this.coverageCode = coverageCode;
    this.coverageName = coverageName == null ? "" : coverageName;
    this.coverageLimit = coverageLimit;
    this.liabilityType = liabilityType;
    this.status = status;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** 新建险种（草稿态）。 */
  public static ClaimCoverage draft(
      Long id,
      Long tenantId,
      Long planId,
      String coverageCode,
      String coverageName,
      BigDecimal coverageLimit,
      String liabilityType) {
    if (planId == null) {
      throw new DomainException("险种必须归属一个计划");
    }
    if (coverageCode == null || coverageCode.isBlank()) {
      throw new DomainException("险种编码不能为空");
    }
    if (coverageLimit != null && coverageLimit.signum() < 0) {
      throw new DomainException("险种额度不能为负");
    }
    String type =
        liabilityType == null || liabilityType.isBlank()
            ? LIABILITY_REIMBURSEMENT
            : liabilityType.trim().toUpperCase(java.util.Locale.ROOT);
    if (!LIABILITY_REIMBURSEMENT.equals(type)
        && !LIABILITY_FIXED_AMOUNT.equals(type)
        && !LIABILITY_ALLOWANCE.equals(type)) {
      throw new DomainException("责任形式只能是 REIMBURSEMENT / FIXED_AMOUNT / ALLOWANCE");
    }
    BigDecimal limit = coverageLimit == null ? BigDecimal.ZERO : coverageLimit;
    return new ClaimCoverage(
        id,
        tenantId,
        planId,
        coverageCode.trim(),
        coverageName,
        limit.setScale(2, java.math.RoundingMode.HALF_UP),
        type,
        ClaimPlan.STATUS_DRAFT,
        Instant.now());
  }

  /** 调整险种额度。 */
  public void changeLimit(BigDecimal newLimit) {
    if (newLimit == null || newLimit.signum() < 0) {
      throw new DomainException("险种额度不能为负");
    }
    this.coverageLimit = newLimit.setScale(2, java.math.RoundingMode.HALF_UP);
    this.updatedAt = Instant.now();
  }

  /** 险种状态（与计划同语义：DRAFT/ACTIVE/RETIRED）。 */
  public String status() {
    return status;
  }

  /** 是否已发布。 */
  public boolean isActive() {
    return ClaimPlan.STATUS_ACTIVE.equals(status);
  }

  /** 所属计划 ID。 */
  public Long planId() {
    return planId;
  }
}
