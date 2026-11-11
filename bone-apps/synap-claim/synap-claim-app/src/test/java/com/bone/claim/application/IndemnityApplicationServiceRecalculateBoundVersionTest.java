package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.catalog.ItemCatalogGateway;
import com.bone.claim.application.port.out.AccumulationPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.application.quota.QuotaYearResolver;
import com.bone.claim.domain.model.adjustment.Accumulation;
import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.repository.AdjustmentRepository;
import com.bone.claim.domain.repository.DecisionExecutionRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.claim.domain.repository.LiabilityFeeRuleRepository;
import com.bone.claim.domain.repository.LiabilityRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P0-1 复算绑定版本单测（G2「配置变更不影响在途案件」，2026-11-11 落地）。
 *
 * <p>验证三件事：① 复算取案件绑定的责任版本（{@code findByUuidAndVersion}），当期 ACTIVE 新版本不参与—— 金额与 {@code formulaHash}
 * 不得漂移；② 绑定版本被清理时 fail-closed（绝不静默回落当期版本）； ③ {@code readjudicate} 显式改绑用例才取当期 ACTIVE。
 *
 * <p>对应《总体技术方案》§5.1 / §13.1（2026-11-11 裁定）与《方案质量诊断报告》§14.1 P0-1 的 「发布新责任版本后重算旧案件，金额 / formulaHash
 * 不变」用例（该用例此前的实现必红，现已转绿）。
 */
class IndemnityApplicationServiceRecalculateBoundVersionTest {

  private static final long TENANT = 42L;
  private static final long CLAIM = 1001L;
  private static final String LIABILITY_UUID = "L-1";
  private static final String BOUND_VERSION = "v1";

  private AdjustmentRepository adjustmentRepository;
  private LiabilityRepository liabilityRepository;
  private LiabilityFeeRuleRepository liabilityFeeRuleRepository;
  private DecisionExecutionRepository decisionExecutionRepository;
  private ClaimCaseApplicationService claimCaseApplicationService;

  private IndemnityApplicationService service;

  @BeforeEach
  void setUp() {
    adjustmentRepository = mock(AdjustmentRepository.class);
    liabilityRepository = mock(LiabilityRepository.class);
    liabilityFeeRuleRepository = mock(LiabilityFeeRuleRepository.class);
    decisionExecutionRepository = mock(DecisionExecutionRepository.class);
    claimCaseApplicationService = mock(ClaimCaseApplicationService.class);

    service =
        new IndemnityApplicationService(
            adjustmentRepository,
            liabilityRepository,
            liabilityFeeRuleRepository,
            mock(EvidenceRepository.class),
            decisionExecutionRepository,
            claimCaseApplicationService,
            mock(AccumulationPort.class),
            mock(ItemCatalogGateway.class),
            mock(QuotaYearResolver.class),
            mock(TenantPort.class),
            mock(com.bone.core.domain.event.DomainEventPublisher.class),
            mock(LiabilityRuleReadGate.class));

    when(adjustmentRepository.findLatestByClaimId(CLAIM))
        .thenReturn(
            Adjustment.calculate(
                9001L,
                TENANT,
                CLAIM,
                rows(),
                liability(BOUND_VERSION, "0.90"),
                "r1",
                Accumulation.MISSING));
    when(claimCaseApplicationService.getClaimWithItems(CLAIM)).thenReturn(rows());
    // versionChainOf 取案件计划版本（versionChain 组成之一）
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getPlanVersion()).thenReturn("pv1");
    when(claimCaseApplicationService.getClaimAggregate(CLAIM)).thenReturn(claim);
    when(liabilityFeeRuleRepository.findByLiabilityUuid(any(), any())).thenReturn(List.of());
    when(decisionExecutionRepository.findLatestByClaimId(CLAIM)).thenReturn(null);
  }

  /** 明细行：200 元有效费用，policyNo 置空以跳过额度/配额年链路（本用例只关心责任版本绑定）。 */
  private static List<ClaimWithItemsProjection> rows() {
    ClaimWithItemsProjection p = new ClaimWithItemsProjection();
    p.setClaimId(CLAIM);
    p.setClaimNo("CL001");
    p.setPolicyNo(null);
    p.setPlanId(7L);
    p.setInsuredIdHash("h");
    p.setClaimType("OUTPATIENT");
    p.setItemId(2001L);
    p.setItemName("门诊诊查");
    p.setItemSocialFlag("0");
    p.setItemValidAmount(new BigDecimal("200"));
    p.setItemSelfPay1(new BigDecimal("0"));
    return List.of(p);
  }

  private static AdjLiability liability(String version, String payPercent) {
    return AdjLiability.of(
        1L,
        TENANT,
        LIABILITY_UUID,
        version,
        7L,
        "R01",
        "REIMBURSEMENT",
        new BigDecimal(payPercent),
        new BigDecimal("50"),
        new BigDecimal("1000"),
        0,
        0,
        "ACTIVE");
  }

  @Test
  @DisplayName("P0-1：发布新版本后复算旧案件——按绑定版本 v1 重算，金额与 formulaHash 不漂移")
  void recalculateStaysOnBoundVersion() {
    // 当期 ACTIVE 已是 v2（90% → 100%）：若复算误取当期配置，金额必然变化，用例即红
    when(liabilityRepository.findActiveByPlanId(7L)).thenReturn(List.of(liability("v2", "1.00")));
    AdjLiability bound = liability(BOUND_VERSION, "0.90");
    when(liabilityRepository.findByUuidAndVersion(LIABILITY_UUID, BOUND_VERSION)).thenReturn(bound);

    Adjustment before = adjustmentRepository.findLatestByClaimId(CLAIM);
    BigDecimal amountBefore = before.getTotalAmount();
    String hashBefore = before.getFormulaHash();

    IndemnityApplicationService.AdjustmentResult result = service.recalculate(CLAIM, "r1");

    assertThat(result.totalAmount()).isEqualByComparingTo(amountBefore);
    assertThat(result.formulaHash()).isEqualTo(hashBefore);
    verify(liabilityRepository).findByUuidAndVersion(LIABILITY_UUID, BOUND_VERSION);
    // 绑定版本命中后绝不再取当期 ACTIVE
    verify(liabilityRepository, never()).findActiveByPlanId(anyLong());
  }

  @Test
  @DisplayName("P0-1：绑定版本被清理 → fail-closed 422，绝不静默回落当期版本")
  void recalculateFailsClosedWhenBoundVersionPurged() {
    when(liabilityRepository.findActiveByPlanId(7L)).thenReturn(List.of(liability("v2", "1.00")));
    when(liabilityRepository.findByUuidAndVersion(LIABILITY_UUID, BOUND_VERSION)).thenReturn(null);

    assertThatThrownBy(() -> service.recalculate(CLAIM, "r1"))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("绑定的责任版本已不可用");
    verify(liabilityRepository, never()).findActiveByPlanId(anyLong());
  }

  @Test
  @DisplayName("readjudicate 显式改绑：取当期 ACTIVE，绑定版本随新条款改写")
  void readjudicateRebindsToCurrentActiveVersion() {
    when(liabilityRepository.findActiveByPlanId(7L)).thenReturn(List.of(liability("v2", "1.00")));

    service.readjudicate(CLAIM, "r1");

    verify(liabilityRepository).findActiveByPlanId(7L);
    Adjustment after = adjustmentRepository.findLatestByClaimId(CLAIM);
    assertThat(after.getLiabilityVersion()).isEqualTo("v2");
    assertThat(after.getLiabilityUuid()).isEqualTo(LIABILITY_UUID);
  }
}
