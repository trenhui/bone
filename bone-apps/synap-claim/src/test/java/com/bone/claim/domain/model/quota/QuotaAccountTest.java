package com.bone.claim.domain.model.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A5 额度账户单测（三值模型守恒 —— 额度泄漏/超赔的第一道防线，§11.7 不变量 1）。
 *
 * <p>核心断言：{@code available = initial − claimed − frozen}，且 claimed/frozen 不得为负。
 */
class QuotaAccountTest {

  private static final long TENANT = 42L;

  private static QuotaAccount account(BigDecimal initial) {
    return QuotaAccount.open(8001L, TENANT, "hash-1", "P1", 7L, 2026, "PERSONAL", "", initial);
  }

  @Test
  @DisplayName("开户：三值归零，可用 = 初始额度")
  void open() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    assertThat(account.getQuotaInitial()).isEqualByComparingTo("20000.00");
    assertThat(account.getQuotaClaimed()).isEqualByComparingTo("0.00");
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("0.00");
    assertThat(account.available()).isEqualByComparingTo("20000.00");
  }

  @Test
  @DisplayName("流水 txn_type 映射矩阵：软占记录 SOFT，硬冻升级记录 HARD 且不二次占用（防双冻结）")
  void txnTypeMatrix() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    QuotaTransaction soft =
        account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "CL001:SOFT:1");

    assertThat(soft.getDirection()).isEqualTo("OCCUPY");
    assertThat(soft.getTxnType()).isEqualTo("SOFT");
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("500.00");

    QuotaTransaction hard =
        account.upgradeToHard(2L, new BigDecimal("500.00"), 1001L, "S1", "CL001:HARD:1");

    assertThat(hard.getTxnType()).isEqualTo("HARD");
    // 硬冻升级不得把 500 变成 1000：同一笔钱在软/硬两个阶段各计一次占用即双冻结
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("500.00");
    assertThat(account.available()).isEqualByComparingTo("19500.00");
    assertThat(hard.getBeforeAmount()).isEqualByComparingTo("500.00");
    assertThat(hard.getAfterAmount()).isEqualByComparingTo("500.00");
  }

  @Test
  @DisplayName("硬冻升级不得超过已冻结额（不得凭空造额度）")
  void upgradeHardCannotExceedFrozen() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "CL001:SOFT:1");
    assertThatThrownBy(
            () -> account.upgradeToHard(2L, new BigDecimal("800.00"), 1001L, "S1", "CL001:HARD:1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不得新增占用");
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("500.00");
  }

  @Test
  @DisplayName("软占计入 frozen 不扣 claimed；可用随之下降")
  void reserveSoftIncreasesFrozen() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "CL001:SOFT:1");

    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("500.00");
    assertThat(account.getQuotaClaimed()).isEqualByComparingTo("0.00");
    assertThat(account.available()).isEqualByComparingTo("19500.00");
  }

  @Test
  @DisplayName("确认：frozen → claimed 同额转移，可用不变（不二次占用）")
  void confirmTransfersWithoutDoubleOccupation() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "CL001:SOFT:1");
    BigDecimal availableBefore = account.available();

    account.confirm(2L, new BigDecimal("500.00"), 1001L, "S1", "CL001:PAID:1");

    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("0.00");
    assertThat(account.getQuotaClaimed()).isEqualByComparingTo("500.00");
    assertThat(account.available()).isEqualByComparingTo(availableBefore);
    assertThat(account.getCapUsed()).isEqualByComparingTo("500.00");
  }

  @Test
  @DisplayName("释放冻结：frozen 归还，可用回升")
  void release() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "CL001:SOFT:1");
    account.release(2L, new BigDecimal("500.00"), 1001L, "S1", "CL001:RELEASE:1");

    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("0.00");
    assertThat(account.available()).isEqualByComparingTo("20000.00");
  }

  @Test
  @DisplayName("回冲已付：claimed 归还（退款/红冲）")
  void rollback() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "CL001:SOFT:1");
    account.confirm(2L, new BigDecimal("500.00"), 1001L, "S1", "CL001:PAID:1");
    account.rollback(3L, new BigDecimal("500.00"), 1001L, "S1", "CL001:RELEASE:2");

    assertThat(account.getQuotaClaimed()).isEqualByComparingTo("0.00");
    assertThat(account.available()).isEqualByComparingTo("20000.00");
  }

  @Test
  @DisplayName("可用额度不足即拒绝占额（防超赔）")
  void insufficientRejected() {
    QuotaAccount account = account(new BigDecimal("300.00"));
    assertThatThrownBy(() -> account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "k"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("可用额度不足");
  }

  @Test
  @DisplayName("并发占额不突破初始额度：多次冻结累计不得超过可用额")
  void neverExceedsInitial() {
    QuotaAccount account = account(new BigDecimal("1000.00"));
    account.reserveSoft(1L, new BigDecimal("600.00"), 1001L, "S1", "k1");
    account.reserveSoft(2L, new BigDecimal("400.00"), 1002L, "S2", "k2");

    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("1000.00");
    assertThat(account.available()).isEqualByComparingTo("0.00");
    assertThatThrownBy(() -> account.reserveSoft(3L, new BigDecimal("1.00"), 1003L, "S3", "k3"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("守恒恒等式恒成立（三值模型不可简化）")
  void conservationAlwaysHolds() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("1500.00"), 1001L, "S1", "k1");
    account.confirm(2L, new BigDecimal("500.00"), 1001L, "S1", "k2");

    BigDecimal expected =
        account
            .getQuotaInitial()
            .subtract(account.getQuotaClaimed())
            .subtract(account.getQuotaFrozen());
    assertThat(account.available()).isEqualByComparingTo(expected);
  }

  @Test
  @DisplayName("每次变动都落流水（账户是投影、流水是事实）")
  void appendsTransactionOnEveryChange() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "CL001:SOFT:1");
    account.confirm(2L, new BigDecimal("500.00"), 1001L, "S1", "CL001:PAID:1");

    List<QuotaTransaction> txns = account.transactions();
    assertThat(txns).hasSize(2);
    assertThat(txns.get(0).getDirection()).isEqualTo("OCCUPY");
    assertThat(txns.get(0).getIdempotencyKey()).isEqualTo("CL001:SOFT:1");
    assertThat(txns.get(1).getTxnType()).isEqualTo("PAID");
  }

  @Test
  @DisplayName("入参校验：初始额度为负 / 金额非正")
  void invariants() {
    assertThatThrownBy(() -> account(new BigDecimal("-1"))).isInstanceOf(DomainException.class);
    QuotaAccount account = account(new BigDecimal("1000.00"));
    assertThatThrownBy(() -> account.reserveSoft(1L, BigDecimal.ZERO, 1001L, "S1", "k"))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> account.confirm(1L, new BigDecimal("10.00"), 1001L, "S1", "k"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("冻结额度小于");
  }

  @Test
  @DisplayName("年免赔累计（R-DED-03）与减人标记")
  void deductibleAndRemoved() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.accumulateDeductible(new BigDecimal("120.50"));
    assertThat(account.getDeductibleUsed()).isEqualByComparingTo("120.50");

    account.markRemoved();
    assertThat(account.isRemoved()).isTrue();
  }
}
