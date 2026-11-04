package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.adjustment.Adjustment;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.settlement.AccountType;
import com.bone.claim.domain.model.settlement.SettlementOrder;
import com.bone.claim.domain.repository.AdjustmentRepository;
import com.bone.claim.domain.repository.SettlementRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 结算建单的「出金依据状态闸」用例。
 *
 * <p>守护的不变量：<b>能读到金额 ≠ 可据此出金</b>。历史实现只判「有理算记录」，于是已作废（{@code ROLLBACKED}）或已解冻待重算（ {@code
 * UNFROZEN}）的理算额仍可被建成结算单并真实占额。
 *
 * <p>为什么同时写正向对照：只写「坏状态被拒」的用例，把闸写成「永远拒绝」也会全绿——那等于把出金通道彻底焊死。
 */
class SettlementSettleableGuardTest {

  private static final long CLAIM_ID = 1001L;
  private static final long TENANT_ID = 9L;
  private static final BigDecimal AMOUNT = new BigDecimal("100.00");

  private SettlementRepository settlementRepository;
  private QuotaApplicationService quotaApplicationService;
  private IndemnityApplicationService indemnityApplicationService;
  private ClaimCaseApplicationService claimCaseApplicationService;
  private TenantPort tenantPort;
  private DomainEventPublisher domainEventPublisher;
  private AdjustmentRepository adjustmentRepository;

  private SettlementApplicationService service;

  @BeforeEach
  void setUp() {
    settlementRepository = mock(SettlementRepository.class);
    quotaApplicationService = mock(QuotaApplicationService.class);
    indemnityApplicationService = mock(IndemnityApplicationService.class);
    claimCaseApplicationService = mock(ClaimCaseApplicationService.class);
    tenantPort = mock(TenantPort.class);
    domainEventPublisher = mock(DomainEventPublisher.class);
    adjustmentRepository = mock(AdjustmentRepository.class);

    service =
        new SettlementApplicationService(
            settlementRepository,
            quotaApplicationService,
            indemnityApplicationService,
            claimCaseApplicationService,
            tenantPort,
            domainEventPublisher,
            adjustmentRepository);
  }

  @Test
  @DisplayName("理算已回滚（ROLLBACKED）→ 拒绝建单，且不落任何结算单")
  void rejectsRollbackedAdjustment() {
    stubReadableAdjustment();
    Adjustment adjustment = adjustmentWith(Adjustment.ResultStatus.ROLLBACKED);
    when(adjustmentRepository.findLatestByClaimId(CLAIM_ID)).thenReturn(adjustment);

    assertThatThrownBy(this::doCreate)
        .isInstanceOf(BizException.class)
        .extracting(ex -> ((BizException) ex).getErrorCode())
        .isEqualTo(ClaimErrorCodes.CLAIM_ADJUST_STATUS_INVALID);

    verify(settlementRepository, never()).insert(any());
  }

  @Test
  @DisplayName("理算已解冻待重算（UNFROZEN）→ 拒绝建单，且不落任何结算单")
  void rejectsUnfrozenAdjustment() {
    stubReadableAdjustment();
    Adjustment adjustment = adjustmentWith(Adjustment.ResultStatus.UNFROZEN);
    when(adjustmentRepository.findLatestByClaimId(CLAIM_ID)).thenReturn(adjustment);

    assertThatThrownBy(this::doCreate)
        .isInstanceOf(BizException.class)
        .extracting(ex -> ((BizException) ex).getErrorCode())
        .isEqualTo(ClaimErrorCodes.CLAIM_ADJUST_STATUS_INVALID);

    verify(settlementRepository, never()).insert(any());
  }

  @Test
  @DisplayName("正向对照：理算 FROZEN → 正常建单占额（闸不得把出金通道焊死）")
  void allowsFrozenAdjustment() {
    stubReadableAdjustment();
    Adjustment adjustment = adjustmentWith(Adjustment.ResultStatus.FROZEN);
    when(adjustmentRepository.findLatestByClaimId(CLAIM_ID)).thenReturn(adjustment);

    SettlementApplicationService.SettlementResult result = doCreate();

    assertThat(result.amount()).isEqualByComparingTo(AMOUNT);
    verify(settlementRepository).insert(any(SettlementOrder.class));
  }

  @Test
  @DisplayName("正向对照：核赔已确认（CONFIRMED）→ 仍可建单（重复建单由 claim+attempt 幂等兜底）")
  void allowsConfirmedAdjustment() {
    stubReadableAdjustment();
    Adjustment adjustment = adjustmentWith(Adjustment.ResultStatus.CONFIRMED);
    when(adjustmentRepository.findLatestByClaimId(CLAIM_ID)).thenReturn(adjustment);

    doCreate();

    verify(settlementRepository).insert(any(SettlementOrder.class));
  }

  // ===================== 夹具 =====================

  private SettlementApplicationService.SettlementResult doCreate() {
    return service.create(CLAIM_ID, 1, AccountType.NO_ACCOUNT, "reviewer-1");
  }

  /** 打桩「理算可读 + 账实可读 + 案件上下文就绪」，使流程能推进到状态闸。 */
  private void stubReadableAdjustment() {
    when(settlementRepository.findLatestByClaimId(CLAIM_ID)).thenReturn(null);
    when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
    when(indemnityApplicationService.latest(CLAIM_ID)).thenReturn(adjustmentResult());
    when(indemnityApplicationService.quotaYear(CLAIM_ID)).thenReturn(2026);
    when(indemnityApplicationService.dailyWindow(CLAIM_ID)).thenReturn(LocalDate.of(2026, 11, 2));

    ClaimWithItemsProjection ctx = mock(ClaimWithItemsProjection.class);
    when(ctx.getClaimNo()).thenReturn("C2026001");
    when(ctx.getInsuredIdHash()).thenReturn("insured-hash-1");
    when(ctx.getPolicyNo()).thenReturn("P2026001");
    when(ctx.getPlanId()).thenReturn(2L);
    when(claimCaseApplicationService.getClaimWithItems(CLAIM_ID)).thenReturn(List.of(ctx));
  }

  private static IndemnityApplicationService.AdjustmentResult adjustmentResult() {
    return new IndemnityApplicationService.AdjustmentResult(
        77L, AMOUNT, "hash-1", "{}", List.of(), "liability-uuid-1", null, BigDecimal.ZERO, true);
  }

  private static Adjustment adjustmentWith(Adjustment.ResultStatus status) {
    Adjustment adjustment = mock(Adjustment.class);
    when(adjustment.resultStatusEnum()).thenReturn(status);
    return adjustment;
  }
}
