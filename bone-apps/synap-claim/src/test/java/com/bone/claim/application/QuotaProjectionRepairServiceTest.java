package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.model.quota.QuotaTransaction;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 额度投影重建（运维修复入口）：只做 f(流水)，三条硬拒绝。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotaProjectionRepairServiceTest {

  private static final long TENANT = 42L;
  private static final long ACCOUNT_ID = 8001L;

  @Mock private QuotaAccountRepository quotaAccountRepository;
  @Mock private TenantPort tenantPort;

  private QuotaProjectionRepairService service;

  @BeforeEach
  void setUp() {
    service = new QuotaProjectionRepairService(quotaAccountRepository, tenantPort);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
  }

  @Test
  @DisplayName("投影与流水一致 ⇒ changed=false，不写库（不制造「已修复」的假日志）")
  void noDriftIsNoOp() {
    QuotaAccount account = account("20000.00");
    account.reconcileProjection(
        new BigDecimal("300.00"), new BigDecimal("200.00"), BigDecimal.ZERO);
    when(quotaAccountRepository.findById(ACCOUNT_ID)).thenReturn(account);
    List<QuotaTransaction> ledger = consistentLedger();
    when(quotaAccountRepository.findTransactions(ACCOUNT_ID)).thenReturn(ledger);

    QuotaProjectionRepairService.RepairResult result =
        service.rebuildProjection(ACCOUNT_ID, "ops-1");

    assertThat(result.changed()).isFalse();
    assertThat(result.deltas()).isEmpty();
    verify(quotaAccountRepository, never()).save(any());
  }

  @Test
  @DisplayName("投影漂移 ⇒ 按流水覆盖，并回传逐列 before/after（差异单之外的第二份证据）")
  void repairsDriftWithColumnDeltas() {
    QuotaAccount account = account("20000.00");
    account.reconcileProjection(
        new BigDecimal("500.00"), new BigDecimal("200.00"), BigDecimal.ZERO);
    when(quotaAccountRepository.findById(ACCOUNT_ID)).thenReturn(account);
    List<QuotaTransaction> ledger = consistentLedger();
    when(quotaAccountRepository.findTransactions(ACCOUNT_ID)).thenReturn(ledger);

    QuotaProjectionRepairService.RepairResult result =
        service.rebuildProjection(ACCOUNT_ID, "ops-1");

    assertThat(result.changed()).isTrue();
    assertThat(result.deltas())
        .extracting(QuotaProjectionRepairService.ColumnDelta::field)
        .containsExactly("quota_frozen");
    assertThat(result.deltas().get(0).before()).isEqualByComparingTo("500.00");
    assertThat(result.deltas().get(0).after()).isEqualByComparingTo("300.00");
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("300.00");
    verify(quotaAccountRepository).save(account);
  }

  @Test
  @DisplayName("流水为空而投影非零 ⇒ 拒绝（按空流水重算等于把占用清零 = 超赔）")
  void refusesEmptyLedger() {
    QuotaAccount account = account("20000.00");
    account.reconcileProjection(new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO);
    when(quotaAccountRepository.findById(ACCOUNT_ID)).thenReturn(account);
    when(quotaAccountRepository.findTransactions(ACCOUNT_ID)).thenReturn(List.of());

    assertThatThrownBy(() -> service.rebuildProjection(ACCOUNT_ID, "ops-1"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SC_CLAIM_QUOTA_STATE_CONFLICT");
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("500.00");
    verify(quotaAccountRepository, never()).save(any());
  }

  @Test
  @DisplayName("重算后占用超过初始额度 ⇒ 拒绝（维护动作不得顺手把守恒破坏写进库）")
  void refusesOverInitial() {
    QuotaAccount account = account("1000.00");
    when(quotaAccountRepository.findById(ACCOUNT_ID)).thenReturn(account);
    List<QuotaTransaction> overDrawn = List.of(txn("SOFT", "OCCUPY", "1500.00", "S1"));
    when(quotaAccountRepository.findTransactions(ACCOUNT_ID)).thenReturn(overDrawn);

    assertThatThrownBy(() -> service.rebuildProjection(ACCOUNT_ID, "ops-1"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SC_CLAIM_QUOTA_STATE_CONFLICT");
    verify(quotaAccountRepository, never()).save(any());
  }

  @Test
  @DisplayName("账户不存在 ⇒ 404 语义（与查询端点同一错误码，运维不会误判成「没漂移」）")
  void accountNotFound() {
    when(quotaAccountRepository.findById(ACCOUNT_ID)).thenReturn(null);

    assertThatThrownBy(() -> service.rebuildProjection(ACCOUNT_ID, "ops-1"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SC_CLAIM_QUOTA_NOT_FOUND");
  }

  private static QuotaAccount account(String initial) {
    return QuotaAccount.open(
        ACCOUNT_ID, TENANT, "hash-1", "P1", 7L, 2026, "PERSONAL", "", new BigDecimal(initial));
  }

  /**
   * 与「冻结 300 + 已付 200」自洽的流水。
   *
   * <p>必须先建好再交给 {@code thenReturn}：在 {@code when(...)} 里调用会造 mock 的 helper，会撞 Mockito 的 {@code
   * UnfinishedStubbing}（外层桩尚未完成时内层又开了一个桩）。
   */
  private static List<QuotaTransaction> consistentLedger() {
    return List.of(
        txn("SOFT", "OCCUPY", "300.00", "S1"),
        txn("HARD", "OCCUPY", "300.00", "S1"),
        txn("PAID", "OCCUPY", "200.00", "S2"));
  }

  private static QuotaTransaction txn(
      String txnType, String direction, String amount, String settlementNo) {
    QuotaTransaction t = mock(QuotaTransaction.class);
    when(t.getTxnType()).thenReturn(txnType);
    when(t.getDirection()).thenReturn(direction);
    when(t.getAmount()).thenReturn(new BigDecimal(amount));
    when(t.getSettlementNo()).thenReturn(settlementNo);
    // HARD 的占用增量取 max(0, after − before)：软占升级场景 before==after ⇒ 增量为 0
    when(t.getBeforeAmount()).thenReturn(new BigDecimal("300.00"));
    when(t.getAfterAmount()).thenReturn(new BigDecimal("300.00"));
    return t;
  }
}
