package com.bone.claim.domain.model.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 额度流水串链（§11.7 不变量 1 ④：{@code after_amount} 首尾相接）。
 *
 * <p>这组用例守的是"过程对不对"：投影判据（{@link QuotaProjection}）只证明"现值 = Σ流水"， 对"两笔操作从同一个旧值出发"完全无感——并发丢失更新后投影仍等于
 * Σ流水，因为投影本来就是按流水算的。
 */
class QuotaLedgerChainTest {

  @Test
  @DisplayName("SOFT→HARD→PAID→RELEASE 全链相接（PAID 只记已扣减却同时减冻结，串链要跟上）")
  void legalChain() {
    List<QuotaTransaction> ledger =
        List.of(
            txn("SOFT", "OCCUPY", "300.00", "0.00", "300.00", "K1"),
            txn("HARD", "OCCUPY", "300.00", "300.00", "300.00", "K2"),
            txn("PAID", "OCCUPY", "200.00", "0.00", "200.00", "K3"),
            txn("RELEASE", "ROLLBACK", "100.00", "100.00", "0.00", "K4"));

    assertThat(QuotaLedgerChain.check(ledger)).isEmpty();
  }

  @Test
  @DisplayName("两笔操作从同一个旧值出发 ⇒ CONTINUITY（并发丢失更新，投影照样等于 Σ流水）")
  void detectsLostUpdate() {
    List<QuotaTransaction> ledger =
        List.of(
            txn("SOFT", "OCCUPY", "300.00", "0.00", "300.00", "K1"),
            txn("SOFT", "OCCUPY", "200.00", "0.00", "200.00", "K2"));

    List<QuotaLedgerChain.Break> breaks = QuotaLedgerChain.check(ledger);

    assertThat(breaks).hasSize(1);
    assertThat(breaks.get(0).kind()).isEqualTo(QuotaLedgerChain.BreakKind.CONTINUITY);
    assertThat(breaks.get(0).column()).isEqualTo(QuotaLedgerChain.COL_FROZEN);
    assertThat(breaks.get(0).expected()).isEqualByComparingTo("300.00");
    assertThat(breaks.get(0).recorded()).isEqualByComparingTo("0.00");
    assertThat(breaks.get(0).idempotencyKey()).isEqualTo("K2");
  }

  @Test
  @DisplayName("after − before 不等于本笔金额 ⇒ ARITHMETIC（金额与前后值不自洽）")
  void detectsArithmeticBreak() {
    List<QuotaTransaction> ledger =
        List.of(txn("SOFT", "OCCUPY", "300.00", "0.00", "250.00", "K1"));

    List<QuotaLedgerChain.Break> breaks = QuotaLedgerChain.check(ledger);

    assertThat(breaks).hasSize(1);
    assertThat(breaks.get(0).kind()).isEqualTo(QuotaLedgerChain.BreakKind.ARITHMETIC);
    assertThat(breaks.get(0).expected()).isEqualByComparingTo("300.00");
    assertThat(breaks.get(0).recorded()).isEqualByComparingTo("250.00");
  }

  @Test
  @DisplayName("DAILY 两侧恒 0 不参与串链（整表串一条链会被它判出一片假红，按列分链才成立）")
  void ignoresDailyFacts() {
    List<QuotaTransaction> ledger =
        List.of(
            txn("SOFT", "OCCUPY", "300.00", "0.00", "300.00", "K1"),
            txn("DAILY", "OCCUPY", "300.00", "0.00", "0.00", "K2"),
            txn("DAILY", "ROLLBACK", "300.00", "0.00", "0.00", "K3"),
            txn("RELEASE", "ROLLBACK", "300.00", "300.00", "0.00", "K4"));

    assertThat(QuotaLedgerChain.check(ledger)).isEmpty();
  }

  @Test
  @DisplayName("首条取 before 为基线：历史账户可能有未入流水的期初占用，判它从 0 起会永久假红")
  void anchorsAtFirstEntry() {
    List<QuotaTransaction> ledger =
        List.of(txn("SOFT", "OCCUPY", "200.00", "500.00", "700.00", "K1"));

    assertThat(QuotaLedgerChain.check(ledger)).isEmpty();
  }

  @Test
  @DisplayName("某列游标变负后停止串链（此后的 before 取自被钳过的账面值，再比只是重复报同一病根）")
  void haltsColumnAfterNegative() {
    List<QuotaTransaction> ledger =
        List.of(
            txn("RELEASE", "ROLLBACK", "100.00", "0.00", "-100.00", "K1"),
            txn("SOFT", "OCCUPY", "50.00", "0.00", "50.00", "K2"));

    assertThat(QuotaLedgerChain.check(ledger)).isEmpty();
  }

  @Test
  @DisplayName("免赔额独立成链：OCCUPY 加、ROLLBACK 减，与冻结额互不干扰")
  void deductibleHasOwnChain() {
    List<QuotaTransaction> ledger =
        List.of(
            txn("DEDUCTIBLE", "OCCUPY", "500.00", "0.00", "500.00", "K1"),
            txn("DEDUCTIBLE", "ROLLBACK", "500.00", "500.00", "0.00", "K2"));

    assertThat(QuotaLedgerChain.check(ledger)).isEmpty();
  }

  @Test
  @DisplayName("免赔额回退却记成占用方向 ⇒ ARITHMETIC（方向写错的典型形态）")
  void deductibleWrongDirection() {
    List<QuotaTransaction> ledger =
        List.of(txn("DEDUCTIBLE", "OCCUPY", "500.00", "500.00", "0.00", "K1"));

    List<QuotaLedgerChain.Break> breaks = QuotaLedgerChain.check(ledger);

    assertThat(breaks).hasSize(1);
    assertThat(breaks.get(0).kind()).isEqualTo(QuotaLedgerChain.BreakKind.ARITHMETIC);
    assertThat(breaks.get(0).column()).isEqualTo(QuotaLedgerChain.COL_DEDUCTIBLE);
  }

  @Test
  @DisplayName("空流水与 null 元素不炸（缺流水由 MISSING_LOCAL 承载，不在这里重复报）")
  void toleratesEmpty() {
    assertThat(QuotaLedgerChain.check(List.of())).isEmpty();
    assertThat(QuotaLedgerChain.check(null)).isEmpty();
    assertThat(QuotaLedgerChain.check(java.util.Arrays.asList(null, null))).isEmpty();
  }

  private static QuotaTransaction txn(
      String txnType, String direction, String amount, String before, String after, String key) {
    QuotaTransaction t = mock(QuotaTransaction.class);
    when(t.getTxnType()).thenReturn(txnType);
    when(t.getDirection()).thenReturn(direction);
    when(t.getAmount()).thenReturn(new BigDecimal(amount));
    when(t.getBeforeAmount()).thenReturn(before == null ? null : new BigDecimal(before));
    when(t.getAfterAmount()).thenReturn(after == null ? null : new BigDecimal(after));
    when(t.getIdempotencyKey()).thenReturn(key);
    return t;
  }
}
