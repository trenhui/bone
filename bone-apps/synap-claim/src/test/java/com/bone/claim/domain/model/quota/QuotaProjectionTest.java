package com.bone.claim.domain.model.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 额度投影重算与漂移检出（§11.7 不变量 1：账户 = Σ 流水）。
 *
 * <p>这组用例守的是"投影与事实同口径"：重算口径被写路径自愈与 T+1 对账共用，一旦两处各写一遍， 就会出现"自愈按 A 口径修、对账按 B 口径判"——修完仍红，或红了却修不掉。
 */
class QuotaProjectionTest {

  private static QuotaTransaction txn(
      String txnType, String direction, String amount, String settlementNo) {
    return txn(txnType, direction, amount, settlementNo, null, null);
  }

  private static QuotaTransaction txn(
      String txnType,
      String direction,
      String amount,
      String settlementNo,
      String before,
      String after) {
    QuotaTransaction t = mock(QuotaTransaction.class);
    when(t.getTxnType()).thenReturn(txnType);
    when(t.getDirection()).thenReturn(direction);
    when(t.getAmount()).thenReturn(new BigDecimal(amount));
    when(t.getSettlementNo()).thenReturn(settlementNo);
    when(t.getBeforeAmount()).thenReturn(before == null ? null : new BigDecimal(before));
    when(t.getAfterAmount()).thenReturn(after == null ? null : new BigDecimal(after));
    return t;
  }

  @Nested
  @DisplayName("重算口径")
  class Rebuild {

    @Test
    @DisplayName("SOFT→HARD→PAID：硬冻升级不二次占用，确认后转为已扣减")
    void softHardPaid() {
      List<QuotaTransaction> ledger =
          List.of(
              txn("SOFT", "OCCUPY", "500.00", "S1"),
              // 新版 HARD 升级：before == after，增量 0（防双冻结）
              txn("HARD", "OCCUPY", "500.00", "S1", "500.00", "500.00"),
              txn("PAID", "OCCUPY", "500.00", "S1"));

      QuotaProjection projection = QuotaProjection.rebuild(ledger);

      assertThat(projection.frozen()).isEqualByComparingTo("0.00");
      assertThat(projection.claimed()).isEqualByComparingTo("500.00");
      assertThat(projection.deductibleUsed()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("老版 HARD 直接起手（before≠after）按真实增量计入，否则其占用永远留在池里")
    void legacyHardCountsRealDelta() {
      QuotaProjection projection =
          QuotaProjection.rebuild(List.of(txn("HARD", "OCCUPY", "500.00", "S1", "0.00", "500.00")));

      assertThat(projection.frozen()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("RELEASE 归还冻结；无对应占用的 RELEASE（越权释放）净额为负，不得抵减他人占用")
    void releaseAndOverRelease() {
      List<QuotaTransaction> ledger =
          List.of(
              txn("SOFT", "OCCUPY", "300.00", "S1"),
              txn("RELEASE", "ROLLBACK", "300.00", "S1"),
              // S2 从未占用就释放：净额 -200，若计入就会把 S1 的占用（或其他单的占用）冲掉
              txn("RELEASE", "ROLLBACK", "200.00", "S2"));

      assertThat(QuotaProjection.rebuild(ledger).frozen()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("REFUND 回冲已扣减，不影响冻结")
    void refundReducesClaimedOnly() {
      List<QuotaTransaction> ledger =
          List.of(
              txn("SOFT", "OCCUPY", "500.00", "S1"),
              txn("PAID", "OCCUPY", "500.00", "S1"),
              txn("REFUND", "ROLLBACK", "200.00", "S1"));

      QuotaProjection projection = QuotaProjection.rebuild(ledger);

      assertThat(projection.claimed()).isEqualByComparingTo("300.00");
      assertThat(projection.frozen()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("DEDUCTIBLE 只动年免赔；DAILY 不动任何余额（非第四本账）")
    void deductibleAndDailyDoNotTouchBalances() {
      List<QuotaTransaction> ledger =
          List.of(
              txn("DEDUCTIBLE", "OCCUPY", "300.00", "S1"),
              txn("DEDUCTIBLE", "ROLLBACK", "100.00", "S1"),
              txn("DAILY", "OCCUPY", "800.00", "S1"));

      QuotaProjection projection = QuotaProjection.rebuild(ledger);

      assertThat(projection.deductibleUsed()).isEqualByComparingTo("200.00");
      assertThat(projection.frozen()).isEqualByComparingTo("0.00");
      assertThat(projection.claimed()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("从未消抵过免赔的结算单去回退 ⇒ 净额为负，不得抵减他人已消抵的免赔")
    void foreignDeductibleRollbackIsIgnored() {
      // S2 没有对应的 OCCUPY 就回退 100：若计入，S1 已消抵的 300 会被抵成 200 ⇒ 客户下一案重复消化免赔（少赔）
      List<QuotaTransaction> ledger =
          List.of(
              txn("DEDUCTIBLE", "OCCUPY", "300.00", "S1"),
              txn("DEDUCTIBLE", "ROLLBACK", "100.00", "S2"));

      assertThat(QuotaProjection.rebuild(ledger).deductibleUsed()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("结算单号缺失的流水单独成组，不被丢弃（丢弃 = 占用在投影里凭空消失）")
    void nullSettlementIsNotDropped() {
      QuotaProjection projection =
          QuotaProjection.rebuild(List.of(txn("SOFT", "OCCUPY", "150.00", null)));

      assertThat(projection.frozen()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("空流水 ⇒ 全零（不是 null）")
    void emptyLedger() {
      QuotaProjection projection = QuotaProjection.rebuild(List.of());

      assertThat(projection.frozen()).isEqualByComparingTo("0.00");
      assertThat(projection.claimed()).isEqualByComparingTo("0.00");
      assertThat(projection.deductibleUsed()).isEqualByComparingTo("0.00");
    }
  }

  @Nested
  @DisplayName("漂移检出")
  class Drift {

    @Test
    @DisplayName("写路径产出的账户与按流水重算一致（同口径的最强证明）")
    void writePathAgreesWithRebuild() {
      QuotaAccount account =
          QuotaAccount.open(
              1L, 9L, "h", "P1", 7L, 2026, "PERSONAL", "", new BigDecimal("20000.00"));
      account.reserveSoft(1L, new BigDecimal("500.00"), 100L, "SO1", "C1:SOFT:1");
      account.upgradeToHard(2L, new BigDecimal("500.00"), 100L, "SO1", "C1:HARD:1");
      account.confirm(3L, new BigDecimal("500.00"), 100L, "SO1", "C1:PAID:1");
      account.accumulateDeductible(4L, new BigDecimal("300.00"), 100L, "SO1", "C1:DED:1");
      account.reserveSoft(5L, new BigDecimal("400.00"), 101L, "SO2", "C2:SOFT:1");

      List<QuotaProjection.Drift> drifts =
          QuotaProjection.rebuild(account.getTransactions()).driftAgainst(account);

      assertThat(drifts).isEmpty();
    }

    @Test
    @DisplayName("cap_used 与 claimed 不等即漂移（冗余列必须被校验，否则是第二真源）")
    void capUsedMustMirrorClaimed() {
      QuotaAccount account =
          QuotaAccount.open(
              1L, 9L, "h", "P1", 7L, 2026, "PERSONAL", "", new BigDecimal("20000.00"));
      account.reserveSoft(1L, new BigDecimal("500.00"), 100L, "SO1", "C1:SOFT:1");
      account.confirm(2L, new BigDecimal("500.00"), 100L, "SO1", "C1:PAID:1");

      // 模拟历史漂移：只把账户上的 cap_used getter 打歪，流水不变
      QuotaAccount drifted = mock(QuotaAccount.class);
      when(drifted.getQuotaClaimed()).thenReturn(new BigDecimal("500.00"));
      when(drifted.getQuotaFrozen()).thenReturn(new BigDecimal("0.00"));
      when(drifted.getDeductibleUsed()).thenReturn(new BigDecimal("0.00"));
      when(drifted.getCapUsed()).thenReturn(new BigDecimal("200.00"));

      List<QuotaProjection.Drift> drifts =
          QuotaProjection.rebuild(account.getTransactions()).driftAgainst(drifted);

      assertThat(drifts).hasSize(1);
      assertThat(drifts.get(0).field()).isEqualTo("cap_used");
      assertThat(drifts.get(0).projection()).isEqualByComparingTo("200.00");
      assertThat(drifts.get(0).ledger()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("逐列报告差异，不是只回一个布尔（风控要知道哪一列错了、错多少）")
    void reportsEveryDriftedColumn() {
      QuotaAccount account = mock(QuotaAccount.class);
      when(account.getQuotaClaimed()).thenReturn(new BigDecimal("900.00"));
      when(account.getQuotaFrozen()).thenReturn(new BigDecimal("800.00"));
      when(account.getDeductibleUsed()).thenReturn(new BigDecimal("700.00"));
      when(account.getCapUsed()).thenReturn(new BigDecimal("600.00"));

      List<QuotaProjection.Drift> drifts = QuotaProjection.rebuild(List.of()).driftAgainst(account);

      assertThat(drifts)
          .extracting(QuotaProjection.Drift::field)
          .containsExactly("quota_frozen", "quota_claimed", "deductible_used", "cap_used");
    }
  }

  @Nested
  @DisplayName("HARD 增量算式")
  class HardDelta {

    @Test
    @DisplayName("before/after 缺失按 0（宁可少算也不凭空计占用）")
    void missingAmounts() {
      assertThat(QuotaProjection.hardDelta(txn("HARD", "OCCUPY", "500.00", "S1")))
          .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("after < before 不为负（不把已释放的占用算成负数）")
    void neverNegative() {
      assertThat(
              QuotaProjection.hardDelta(txn("HARD", "OCCUPY", "500.00", "S1", "500.00", "200.00")))
          .isEqualByComparingTo("0.00");
    }
  }
}
