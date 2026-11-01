package com.bone.claim.domain.model.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 配置面编辑契约（CRUD 补全）：保单/计划/险种的可修正字段编辑与不变式。
 *
 * <p><b>为什么要这个测试</b>：编辑是新增能力，且保单在「启用中」锁定承保期间是刻意引入的不变式。 无测试覆盖的编辑 = "配了不生效"的头号正确性债务——本仓已踩过多次。
 * 这里用<b>变异视角</b>断言：删掉 {@code edit} 里任意一行赋值，用例都应转红（字段确实被改写了，而非空转）。
 */
class ClaimConfigEditContractTest {

  private static final long TENANT = 42L;
  private static final long ID = 1001L;

  private static ClaimPolicy openedPolicy() {
    return ClaimPolicy.open(
        ID,
        TENANT,
        "POL-1",
        "保单A",
        "保司",
        "分公司",
        "投保方",
        "BIZ-1",
        "OUTPATIENT",
        "GROUP",
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-12-31T00:00:00Z"),
        30,
        "v1",
        "[\"h1\",\"h2\"]",
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static ClaimPlan draftedPlan() {
    return ClaimPlan.draft(
        ID, TENANT, "POL-1", "PLAN-1", "门诊计划", BigDecimal.valueOf(1000), 15, "v1");
  }

  private static ClaimCoverage draftedCoverage() {
    return ClaimCoverage.draft(
        ID, TENANT, 7L, "COV-1", "门诊险种", BigDecimal.valueOf(500), "REIMBURSEMENT");
  }

  @Test
  @DisplayName("保单编辑：可修正字段被改写，且 updatedAt 前进")
  void policyEditMutatesFields() {
    ClaimPolicy p = openedPolicy();
    Instant before = p.getUpdatedAt();

    p.edit(
        "保单A-改",
        "保司-改",
        "分公司-改",
        "投保方-改",
        "BIZ-9",
        "INPATIENT",
        "GROUP",
        Instant.parse("2026-02-01T00:00:00Z"),
        Instant.parse("2027-01-31T00:00:00Z"),
        45,
        "v2",
        "[\"h3\"]");

    assertThat(p.getPolicyName()).isEqualTo("保单A-改");
    assertThat(p.getInsurerName()).isEqualTo("保司-改");
    assertThat(p.getProductType()).isEqualTo("INPATIENT");
    assertThat(p.getWaitingPeriod()).isEqualTo(45);
    assertThat(p.getPolicyVersion()).isEqualTo("v2");
    assertThat(p.getInsuredSnapshot()).isEqualTo("[\"h3\"]");
    assertThat(p.getUpdatedAt()).isAfter(before);
    // 保单号与规则开关不可改
    assertThat(p.getPolicyNo()).isEqualTo("POL-1");
    assertThat(p.configStatus()).isEqualTo(ClaimPolicy.CONFIG_INACTIVE);
  }

  @Test
  @DisplayName("保单编辑：等待期为负抛 DomainException")
  void policyEditRejectsNegativeWaitingPeriod() {
    ClaimPolicy p = openedPolicy();
    assertThatThrownBy(
            () ->
                p.edit(
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    Instant.parse("2026-02-01T00:00:00Z"),
                    Instant.parse("2027-01-31T00:00:00Z"),
                    -1,
                    "v2",
                    "[]"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("保单编辑：启用中改承保期间被拒绝（须先停用）")
  void policyEditLocksDatesWhenActive() {
    ClaimPolicy p = openedPolicy();
    p.activate("op");
    assertThat(p.configStatus()).isEqualTo(ClaimPolicy.CONFIG_ACTIVE);

    assertThatThrownBy(
            () ->
                p.edit(
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    Instant.parse("2025-01-01T00:00:00Z"),
                    Instant.parse("2025-12-31T00:00:00Z"),
                    30,
                    "v2",
                    "[]"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("保单编辑：启用中仅改名称（期间不变）放行")
  void policyEditAllowsNonDateChangeWhenActive() {
    ClaimPolicy p = openedPolicy();
    p.activate("op");
    p.edit(
        "改名",
        "x",
        "x",
        "x",
        "x",
        "x",
        "GROUP",
        p.getEffectiveStart(),
        p.getEffectiveEnd(),
        30,
        "v2",
        p.getInsuredSnapshot());
    assertThat(p.getPolicyName()).isEqualTo("改名");
  }

  @Test
  @DisplayName("保单编辑：生效起不早于生效止抛 DomainException")
  void policyEditRejectsReversedPeriod() {
    ClaimPolicy p = openedPolicy();
    assertThatThrownBy(
            () ->
                p.edit(
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    Instant.parse("2027-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    30,
                    "v2",
                    "[]"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("计划编辑：可修正字段被改写，编码/状态不变")
  void planEditMutatesFields() {
    ClaimPlan plan = draftedPlan();
    plan.edit("门诊计划-改", BigDecimal.valueOf(2000), 20, "v3");
    assertThat(plan.getPlanName()).isEqualTo("门诊计划-改");
    assertThat(plan.getPlanLimit()).isEqualByComparingTo("2000.00");
    assertThat(plan.waitingPeriodDays()).isEqualTo(20);
    assertThat(plan.getVersionNo()).isEqualTo("v3");
    assertThat(plan.status()).isEqualTo(ClaimPlan.STATUS_DRAFT);
  }

  @Test
  @DisplayName("计划编辑：额度为负抛 DomainException")
  void planEditRejectsNegativeLimit() {
    ClaimPlan plan = draftedPlan();
    assertThatThrownBy(() -> plan.edit("x", BigDecimal.valueOf(-1), 0, "v1"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("险种编辑：可修正字段被改写，编码/状态不变")
  void coverageEditMutatesFields() {
    ClaimCoverage c = draftedCoverage();
    c.edit("门诊险种-改", BigDecimal.valueOf(800), "FIXED_AMOUNT");
    assertThat(c.getCoverageName()).isEqualTo("门诊险种-改");
    assertThat(c.getCoverageLimit()).isEqualByComparingTo("800.00");
    assertThat(c.getLiabilityType()).isEqualTo(ClaimCoverage.LIABILITY_FIXED_AMOUNT);
    assertThat(c.status()).isEqualTo(ClaimPlan.STATUS_DRAFT);
  }

  @Test
  @DisplayName("险种编辑：非法责任形式抛 DomainException")
  void coverageEditRejectsInvalidLiabilityType() {
    ClaimCoverage c = draftedCoverage();
    assertThatThrownBy(() -> c.edit("x", BigDecimal.ZERO, "NOPE"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("险种编辑：额度为负抛 DomainException")
  void coverageEditRejectsNegativeLimit() {
    ClaimCoverage c = draftedCoverage();
    assertThatThrownBy(() -> c.edit("x", BigDecimal.valueOf(-5), "ALLOWANCE"))
        .isInstanceOf(DomainException.class);
  }
}
