package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.QuotaQueryService.QuotaSummary;
import com.bone.claim.application.quota.QuotaYearResolver;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * A5 额度读模型：赔案不存在 vs 账户未开立必须给出<b>不同</b>的响应。
 *
 * <p>"赔案查不到"是真 404；"这个案子还没有额度账户"是一个需要被看见的状态（年度口径切换 / 开户未跑 / 行键缺失）， 一律 404 只会让运维拿着一个错误码去数据库里猜。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotaQueryServiceTest {

  private static final long CLAIM_ID = 1001L;
  private static final long PLAN_ID = 7L;

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private QuotaAccountRepository quotaAccountRepository;

  private QuotaQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new QuotaQueryService(
            claimCaseRepository,
            quotaAccountRepository,
            QuotaYearResolver.of(QuotaYearResolver.Basis.CALENDAR, null));
  }

  @Test
  @DisplayName("赔案不存在 ⇒ 仍是 404（这是真的没有资源）")
  void claimNotFound() {
    when(claimCaseRepository.findClaimWithItems(CLAIM_ID)).thenReturn(List.of());

    assertThatThrownBy(() -> service.getByClaim(CLAIM_ID))
        .isInstanceOf(BizException.class)
        .hasMessageContaining(ClaimErrorCodes.CLAIM_NOT_FOUND);
  }

  @Test
  @DisplayName("账户未开立 ⇒ opened=false + 带回去的年度，而不是 404")
  void accountMissingIsAStateNotA404() {
    givenClaim(row("P1", PLAN_ID));
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(null);

    QuotaSummary summary = service.getByClaim(CLAIM_ID);

    assertThat(summary.opened()).isFalse();
    assertThat(summary.quotaYear()).isEqualTo(2026);
    assertThat(summary.quotaYearBasis()).isEqualTo("CALENDAR");
    assertThat(summary.available()).isEqualByComparingTo("0.00");
    assertThat(summary.transactions()).isEmpty();
    // 未开户不是封顶：混成一个 true 会让工作台"已达封顶"筛选把未开户的案子也抓进来
    assertThat(summary.capReached()).isFalse();
  }

  @Test
  @DisplayName("行键缺计划号 ⇒ 年度不可确定（null），不猜一个年份")
  void incompleteRowKeyGivesUnknownYear() {
    givenClaim(row("P1", null));

    QuotaSummary summary = service.getByClaim(CLAIM_ID);

    assertThat(summary.opened()).isFalse();
    assertThat(summary.quotaYear()).isNull();
    assertThat(summary.quotaYearBasis()).isEqualTo("CALENDAR");
  }

  @Test
  @DisplayName("正常账户：三值 + 免赔/封顶投影 + 流水映射，未用尽时 capReached=false")
  void mapsAccountProjections() {
    QuotaAccount account =
        QuotaAccount.open(
            1L, 9L, "h", "P1", PLAN_ID, 2026, "PERSONAL", "", new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), CLAIM_ID, "SO1", "C1:SOFT:1");
    account.confirm(2L, new BigDecimal("500.00"), CLAIM_ID, "SO1", "C1:PAID:1");
    account.accumulateDeductible(3L, new BigDecimal("300.00"), CLAIM_ID, "SO1", "C1:DED:1");

    givenClaim(row("P1", PLAN_ID));
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(account);
    when(quotaAccountRepository.findTransactions(1L)).thenReturn(account.getTransactions());

    QuotaSummary summary = service.getByClaim(CLAIM_ID);

    assertThat(summary.opened()).isTrue();
    assertThat(summary.quotaYear()).isEqualTo(2026);
    assertThat(summary.initial()).isEqualByComparingTo("20000.00");
    assertThat(summary.claimed()).isEqualByComparingTo("500.00");
    assertThat(summary.frozen()).isEqualByComparingTo("0.00");
    assertThat(summary.available()).isEqualByComparingTo("19500.00");
    assertThat(summary.deductibleUsed()).isEqualByComparingTo("300.00");
    assertThat(summary.capUsed()).isEqualByComparingTo("500.00");
    assertThat(summary.capReached()).isFalse();
    assertThat(summary.transactions()).hasSize(3);
  }

  @Test
  @DisplayName("额度用尽（含冻结占用）⇒ capReached=true：冻着的钱已经占掉额度")
  void capReachedIncludesFrozen() {
    QuotaAccount account =
        QuotaAccount.open(
            1L, 9L, "h", "P1", PLAN_ID, 2026, "PERSONAL", "", new BigDecimal("500.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), CLAIM_ID, "SO1", "C1:SOFT:1");

    givenClaim(row("P1", PLAN_ID));
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(account);
    when(quotaAccountRepository.findTransactions(1L)).thenReturn(account.getTransactions());

    QuotaSummary summary = service.getByClaim(CLAIM_ID);

    assertThat(summary.available()).isEqualByComparingTo("0.00");
    assertThat(summary.capReached()).isTrue();
  }

  @Test
  @DisplayName("流水为空（历史账户无流水）不炸：transactions 为空列表")
  void toleratesEmptyLedger() {
    QuotaAccount account =
        QuotaAccount.open(
            1L, 9L, "h", "P1", PLAN_ID, 2026, "PERSONAL", "", new BigDecimal("20000.00"));
    givenClaim(row("P1", PLAN_ID));
    when(quotaAccountRepository.findAccount(any(), any(), anyLong(), anyInt(), any(), any()))
        .thenReturn(account);
    when(quotaAccountRepository.findTransactions(1L)).thenReturn(null);

    assertThat(service.getByClaim(CLAIM_ID).transactions()).isEmpty();
  }

  /**
   * 案件投影桩。
   *
   * <p>必须先构造行、再调本方法：若写成 {@code when(repo.find(...)).thenReturn(List.of(row()))}， {@code row()} 里的
   * {@code when(...)} 会在外层桩尚未完成时执行，撞 Mockito 的 UnfinishedStubbing。
   */
  private void givenClaim(ClaimWithItemsProjection row) {
    when(claimCaseRepository.findClaimWithItems(CLAIM_ID)).thenReturn(List.of(row));
  }

  /** 案件投影桩：发票日 2026-03-05 ⇒ 自然年口径下年度为 2026。 */
  private static ClaimWithItemsProjection row(String policyNo, Long planId) {
    ClaimWithItemsProjection row = mock(ClaimWithItemsProjection.class);
    when(row.getPolicyNo()).thenReturn(policyNo);
    when(row.getPlanId()).thenReturn(planId);
    when(row.getInsuredIdHash()).thenReturn("h");
    when(row.getInvoiceDate()).thenReturn(LocalDate.of(2026, 3, 5));
    return row;
  }
}
