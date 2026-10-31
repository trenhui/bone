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

    // 幂等键：重复消抵同一笔免赔必须被流水判重拦住，否则撤件重算会把免赔吃掉两次 ⇒ 系统性少赔
    assertThat(account.accumulateDeductible(1L, new BigDecimal("120.50"), 1001L, "S1", "C1:DED:1"))
        .isNotNull();
    assertThat(account.getDeductibleUsed()).isEqualByComparingTo("120.50");

    // 回退对称：撤件后免赔须归还，否则客户下一案要重新消化一笔根本没赔出去的免赔
    account.reduceDeductible(2L, new BigDecimal("120.50"), 1001L, "S1", "C1:DED_ROLLBACK:1");
    assertThat(account.getDeductibleUsed()).isEqualByComparingTo("0.00");

    account.accumulateDeductible(3L, new BigDecimal("80.00"), 1002L, "S2", "C2:DED:1");
    account.reduceDeductible(4L, new BigDecimal("9999.00"), 1002L, "S2", "C2:DED_ROLLBACK:1");
    assertThat(account.getDeductibleUsed())
        .as("回退金额超过已累计时钳到 0，不得出现负免赔反向放大赔付基数")
        .isEqualByComparingTo("0.00");

    account.markRemoved();
    assertThat(account.isRemoved()).isTrue();
  }

  // ===================== 释放归属：只还自己的钱（P0-5） =====================

  @Test
  @DisplayName("releaseOwned：本单无自有预占时是空操作，不得触碰共享池（越权释放防线）")
  void releaseOwnedWithoutOwnHoldIsNoop() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    // 别的结算单占着的 5000：共享池里混着多单预占
    account.reserveSoft(1L, new BigDecimal("5000.00"), 2002L, "S-OTHER", "OTHER:SOFT:1");

    QuotaTransaction released =
        account.releaseOwned(
            2L, new BigDecimal("3000.00"), BigDecimal.ZERO, 1001L, "S-MINE", "MINE:RELEASE:1");

    assertThat(released).as("本单自有预占为 0 ⇒ 空操作，不落流水").isNull();
    assertThat(account.getQuotaFrozen()).as("共享池冻结额不得被本单释放改动").isEqualByComparingTo("5000.00");
    assertThat(account.getTransactions()).as("空操作不得新增流水（只应保留他人那笔 SOFT）").hasSize(1);
  }

  @Test
  @DisplayName("releaseOwned：本单自有预占不足时必须 fail-closed，拒绝按池余额放行")
  void releaseOwnedRejectsInsufficientOwnHold() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("10000.00"), 1001L, "S-MINE", "MINE:SOFT:1");
    // 历史上已释放过一部分：本单自有预占只剩 4000
    account.release(2L, new BigDecimal("6000.00"), 1001L, "S-MINE", "MINE:LEGACY_PARTIAL:1");

    assertThatThrownBy(
            () ->
                account.releaseOwned(
                    3L,
                    new BigDecimal("10000.00"),
                    new BigDecimal("4000.00"),
                    1001L,
                    "S-MINE",
                    "MINE:RELEASE:1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("拒绝越权释放");
    assertThat(account.getQuotaFrozen()).as("拒绝后冻结额不得被改动").isEqualByComparingTo("4000.00");
  }

  @Test
  @DisplayName("releaseOwned：自有预占充足时正常归还，并落 RELEASE 流水")
  void releaseOwnedWithEnoughHoldReleases() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("10000.00"), 1001L, "S-MINE", "MINE:SOFT:1");

    QuotaTransaction released =
        account.releaseOwned(
            2L,
            new BigDecimal("10000.00"),
            new BigDecimal("10000.00"),
            1001L,
            "S-MINE",
            "MINE:RELEASE:1");

    assertThat(released).isNotNull();
    assertThat(released.getTxnType()).isEqualTo("RELEASE");
    assertThat(released.getDirection()).isEqualTo("ROLLBACK");
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("reduceDeductibleOwned：免赔回退只认本单自有量，不得回退他人已消抵的免赔")
  void reduceDeductibleOwnedRespectsOwnership() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    // 别的案件消抵的免赔：共享池
    account.accumulateDeductible(1L, new BigDecimal("10000.00"), 2002L, "S-OTHER", "OTHER:DED:1");

    QuotaTransaction rolledBack =
        account.reduceDeductibleOwned(
            2L,
            new BigDecimal("10000.00"),
            BigDecimal.ZERO,
            1001L,
            "S-MINE",
            "MINE:DED_ROLLBACK:1");

    assertThat(rolledBack).as("本单从未消抵免赔 ⇒ 空操作").isNull();
    assertThat(account.getDeductibleUsed()).as("不得回退他人已消抵的免赔").isEqualByComparingTo("10000.00");
  }

  @Test
  @DisplayName("流水类型无歧义：回冲已扣减记 REFUND（区别于释放冻结的 RELEASE）")
  void refundTxnTypeIsDistinctFromRelease() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "CL001:SOFT:1");
    account.confirm(2L, new BigDecimal("500.00"), 1001L, "S1", "CL001:PAID:1");
    QuotaTransaction refund =
        account.rollback(3L, new BigDecimal("500.00"), 1001L, "S1", "CL001:REFUND:1");

    assertThat(refund.getTxnType())
        .as("回冲 claimed 必须与释放 frozen 区分：流水是事实表，类型歧义会让「本单还占多少冻结」算错")
        .isEqualTo("REFUND");
    assertThat(refund.getDirection()).isEqualTo("ROLLBACK");
    assertThat(account.getQuotaClaimed()).isEqualByComparingTo("0.00");
  }

  // ===================== 投影自愈：释放不得被共享池余额阻塞（F3） =====================

  @Test
  @DisplayName("归属校验通过但池投影亏空时不得放出负冻结：交由上层按流水自愈，领域层仍 fail-closed")
  void releaseOwnedWithDeficitProjectionFailsOnConservation() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S-MINE", "MINE:SOFT:1");

    // 共享池被他人越权释放抽干：账户投影 frozen 被扣成 0，而本单流水事实（SOFT 500）仍在
    account.reconcileProjection(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("0.00");

    // 自有 hold 足额 ⇒ 归属校验通过；但投影未自愈 ⇒ 扣减后为负 ⇒ 守恒 fail-closed
    assertThatThrownBy(
            () ->
                account.releaseOwned(
                    2L,
                    new BigDecimal("500.00"),
                    new BigDecimal("500.00"),
                    1001L,
                    "S-MINE",
                    "MINE:RELEASE:1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("额度守恒被破坏");
  }

  @Test
  @DisplayName("reconcileProjection：按流水事实重算投影并同步 capUsed，且不得放出负数")
  void reconcileProjectionRestoresProjectionAndSyncsCapUsed() {
    QuotaAccount account = account(new BigDecimal("20000.00"));
    account.reserveSoft(1L, new BigDecimal("500.00"), 1001L, "S1", "CL001:SOFT:1");
    account.confirm(2L, new BigDecimal("500.00"), 1001L, "S1", "CL001:PAID:1");

    account.reconcileProjection(
        new BigDecimal("300.00"), new BigDecimal("500.00"), new BigDecimal("80.00"));

    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("300.00");
    assertThat(account.getQuotaClaimed()).isEqualByComparingTo("500.00");
    assertThat(account.getCapUsed())
        .as("capUsed 必须与 claimed 同步（封顶已用）")
        .isEqualByComparingTo("500.00");
    assertThat(account.getDeductibleUsed()).isEqualByComparingTo("80.00");

    // 负投影必须被钳到 0，不得出现负额度
    account.reconcileProjection(
        new BigDecimal("-100.00"), new BigDecimal("-100.00"), new BigDecimal("-1.00"));
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("0.00");
    assertThat(account.getQuotaClaimed()).isEqualByComparingTo("0.00");
    assertThat(account.getDeductibleUsed()).isEqualByComparingTo("0.00");
  }
}
