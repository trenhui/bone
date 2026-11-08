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
 * 保障计划聚合根（{@code t_claim_plan}）——保单下的计划层（参考实现三层树的第一层）。
 *
 * <p><b>为什么一保单可挂多计划</b>：参考实现 pk-tpa-saas 的 {@code PlanDTO} 自带 {@code policyNo} 字段， 且"计划层 / 险种层 /
 * 责任层"三列并排展示 ⇒ 同一保单下可以有多个计划（如"门诊计划"+"住院计划"）， 每个计划各自带额度。本仓此前 {@code plan_id} 只是裸 {@code Long}
 * 列（{@code t_claim_case} / {@code t_quota_account} / {@code t_adj_liability} 三处引用），既无实体也无归属 ⇒
 * 运营配的计划 无处可查、报案的"计划ID"只能手填。
 *
 * <p><b>状态机</b>：{@code DRAFT} 草稿 → {@code ACTIVE} 已发布 → {@code RETIRED} 已下线。 草稿态的责任配置不参与理算（理算只读
 * {@code ACTIVE} 责任），这与"已生效版本不可改"（§4.1）配套： 改配置不是改已发布行，而是发新版本。
 *
 * <p><b>不变量</b>：计划额度不能为负；等待期不能为负；{@code status} 只能取三值之一。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_plan")
public class ClaimPlan extends TenantAggregateRoot<Long> {

  /** 草稿：配置中，不参与理算。 */
  public static final String STATUS_DRAFT = "DRAFT";

  /** 已发布：参与理算。 */
  public static final String STATUS_ACTIVE = "ACTIVE";

  /** 已下线：不再接受新配置，历史责任保留可查。 */
  public static final String STATUS_RETIRED = "RETIRED";

  @Column(name = "policy_no", nullable = false)
  private String policyNo;

  @Column(name = "plan_code", nullable = false)
  private String planCode;

  @Column(name = "plan_name")
  private String planName;

  @Column(name = "plan_limit", nullable = false)
  private BigDecimal planLimit;

  @Column(name = "waiting_period")
  private Integer waitingPeriod;

  @Column(name = "version_no")
  private String versionNo;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  private ClaimPlan(
      Long id,
      Long tenantId,
      String policyNo,
      String planCode,
      String planName,
      BigDecimal planLimit,
      int waitingPeriod,
      String versionNo,
      String status,
      Instant now) {
    setId(id);
    setTenantId(tenantId);
    this.policyNo = policyNo;
    this.planCode = planCode;
    this.planName = planName == null ? "" : planName;
    this.planLimit = planLimit;
    this.waitingPeriod = waitingPeriod;
    this.versionNo = versionNo == null || versionNo.isBlank() ? "v1" : versionNo;
    this.status = status;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** 新建计划（草稿态）。 */
  public static ClaimPlan draft(
      Long id,
      Long tenantId,
      String policyNo,
      String planCode,
      String planName,
      BigDecimal planLimit,
      int waitingPeriod,
      String versionNo) {
    if (policyNo == null || policyNo.isBlank()) {
      throw new DomainException("计划必须归属一张保单");
    }
    if (planCode == null || planCode.isBlank()) {
      throw new DomainException("计划编码不能为空");
    }
    if (planLimit != null && planLimit.signum() < 0) {
      throw new DomainException("计划额度不能为负");
    }
    if (waitingPeriod < 0) {
      throw new DomainException("等待期不能为负");
    }
    BigDecimal limit = planLimit == null ? BigDecimal.ZERO : planLimit;
    return new ClaimPlan(
        id,
        tenantId,
        policyNo.trim(),
        planCode.trim(),
        planName,
        limit.setScale(2, java.math.RoundingMode.HALF_UP),
        waitingPeriod,
        versionNo,
        STATUS_DRAFT,
        Instant.now());
  }

  /** 发布计划（DRAFT → ACTIVE）：激活后其下 ACTIVE 责任才参与理算。 */
  public void publish(String operatorId) {
    if (STATUS_ACTIVE.equals(status)) {
      return;
    }
    if (STATUS_RETIRED.equals(status)) {
      throw new DomainException("计划已下线，不可再发布");
    }
    String from = status;
    this.status = STATUS_ACTIVE;
    this.updatedAt = Instant.now();
    addDomainEvent(
        new com.bone.claim.domain.model.policy.event.PlanStatusChangedEvent(
            getId(),
            getTenantId(),
            policyNo,
            planCode,
            from,
            STATUS_ACTIVE,
            operatorId,
            updatedAt));
  }

  /** 下线计划（ACTIVE/DRAFT → RETIRED）。 */
  public void retire(String operatorId) {
    if (STATUS_RETIRED.equals(status)) {
      return;
    }
    String from = status;
    this.status = STATUS_RETIRED;
    this.updatedAt = Instant.now();
    addDomainEvent(
        new com.bone.claim.domain.model.policy.event.PlanStatusChangedEvent(
            getId(),
            getTenantId(),
            policyNo,
            planCode,
            from,
            STATUS_RETIRED,
            operatorId,
            updatedAt));
  }

  /** 调整计划额度。 */
  public void changeLimit(BigDecimal newLimit) {
    if (newLimit == null || newLimit.signum() < 0) {
      throw new DomainException("计划额度不能为负");
    }
    this.planLimit = newLimit.setScale(2, java.math.RoundingMode.HALF_UP);
    this.updatedAt = Instant.now();
  }

  /** 调整等待期。 */
  public void changeWaitingPeriod(int days) {
    if (days < 0) {
      throw new DomainException("等待期不能为负");
    }
    this.waitingPeriod = days;
    this.updatedAt = Instant.now();
  }

  /**
   * 编辑计划可修正字段（不改动计划编码与状态）。
   *
   * <p><b>不改计划编码</b>：计划编码是险种、额度账户、赔案的主数据键，改名会制造孤儿引用； 如需换码应走"新计划 + 迁移险种"而非原地改键。额度/等待期/名称/版本可随时改。
   * 下调额度不会回收已占用额度（与账户不同，计划的"额度"是配置上限而非余额）， 仅作为后续理算的约束，故此处不校验下界之外的守恒。
   */
  public void edit(String planName, BigDecimal planLimit, int waitingPeriod, String versionNo) {
    if (planLimit != null && planLimit.signum() < 0) {
      throw new DomainException("计划额度不能为负");
    }
    if (waitingPeriod < 0) {
      throw new DomainException("等待期不能为负");
    }
    this.planName = planName == null ? "" : planName;
    this.planLimit =
        (planLimit == null ? BigDecimal.ZERO : planLimit)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    this.waitingPeriod = waitingPeriod;
    this.versionNo = versionNo == null || versionNo.isBlank() ? "v1" : versionNo;
    this.updatedAt = Instant.now();
  }

  /** 计划状态。 */
  public String status() {
    return status;
  }

  /** 是否已发布（参与理算）。 */
  public boolean isActive() {
    return STATUS_ACTIVE.equals(status);
  }

  /** 所属保单号。 */
  public String policyNo() {
    return policyNo;
  }

  /** 等待期天数。 */
  public int waitingPeriodDays() {
    return waitingPeriod == null ? 0 : waitingPeriod;
  }
}
