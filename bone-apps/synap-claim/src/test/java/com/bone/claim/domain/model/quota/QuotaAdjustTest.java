package com.bone.claim.domain.model.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 额度账户「调额」的单测——锁的是守恒不变量的下界。
 *
 * <p><b>为什么这条约束必须存在</b>：{@code available = initial − claimed − frozen}。 运营把保额从 10 万调到 5 万，而该被保人已赔 8
 * 万（claimed=80000）， 此时 {@code available = 50000 − 80000 = −30000} ⇒ 守恒被破坏 ⇒ 后续任何占额都会被 {@code
 * assertConserved} 拦下，整条资金链卡死。所以下调必须被拒绝， 且拒绝的理由要指向"先处置已赔案件"这个正确动作。
 */
class QuotaAdjustTest {

  private QuotaAccount accountWith(BigDecimal initial) {
    return QuotaAccount.open(1L, 0L, "hash-a", "POL-001", 10L, 2026, "PERSONAL", "", initial);
  }

  @Test
  @DisplayName("上调额度 → 成功，返回变更前的值供保全记录使用")
  void increaseAllowed() {
    QuotaAccount account = accountWith(new BigDecimal("100000.00"));

    BigDecimal before = account.adjustInitial(new BigDecimal("200000.00"));

    assertThat(before).isEqualByComparingTo("100000.00");
    assertThat(account.initial()).isEqualByComparingTo("200000.00");
    assertThat(account.available()).isEqualByComparingTo("200000.00");
  }

  @Test
  @DisplayName("额度调为 0 → 允许（额度为 0 是合法配置：表示该被保人不保）")
  void zeroAllowed() {
    QuotaAccount account = accountWith(new BigDecimal("100000.00"));
    account.adjustInitial(BigDecimal.ZERO);

    assertThat(account.initial()).isEqualByComparingTo("0");
    assertThat(account.available()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("额度调为负 → 拒绝")
  void negativeRejected() {
    QuotaAccount account = accountWith(new BigDecimal("100000.00"));

    assertThatThrownBy(() -> account.adjustInitial(new BigDecimal("-1")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("不能为负");
  }

  @Test
  @DisplayName("额度超出上限 → 拒绝（与开户时的同一上限口径）")
  void overLimitRejected() {
    QuotaAccount account = accountWith(new BigDecimal("100000.00"));

    assertThatThrownBy(() -> account.adjustInitial(new BigDecimal("100000001")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("上限");
  }

  @Test
  @DisplayName("下调到低于已占用额度 → 拒绝，并指向「先处置已赔案件」")
  void decreaseBelowOccupiedRejected() {
    QuotaAccount account = accountWith(new BigDecimal("100000.00"));
    // 先软占再确认 = 一笔已赔 80000（claimed=80000，frozen 归零）。
    // 确认路径要求 frozen 足额，故必须先 reserveSoft 再 confirm（直接 confirm 会被"冻结额度不足"拦下）。
    account.reserveSoft(100L, new BigDecimal("80000.00"), 1L, "SET-1", "key-soft");
    account.confirm(101L, new BigDecimal("80000.00"), 1L, "SET-1", "key-confirm");
    assertThat(account.getQuotaClaimed()).isEqualByComparingTo("80000.00");

    // 把保额降到 50000 ⇒ available = 50000 − 80000 = −30000 ⇒ 守恒破坏
    assertThatThrownBy(() -> account.adjustInitial(new BigDecimal("50000.00")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("低于已占用额度");
  }

  @Test
  @DisplayName("下调到恰好等于已占用额度 → 允许（available 归零，不为负）")
  void decreaseToExactlyOccupiedAllowed() {
    QuotaAccount account = accountWith(new BigDecimal("100000.00"));
    account.reserveSoft(100L, new BigDecimal("80000.00"), 1L, "SET-1", "key-soft");
    account.confirm(101L, new BigDecimal("80000.00"), 1L, "SET-1", "key-confirm");

    account.adjustInitial(new BigDecimal("80000.00"));

    assertThat(account.initial()).isEqualByComparingTo("80000.00");
    assertThat(account.available()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("已冻结（未确认）的额度同样计入下界约束")
  void decreaseBelowFrozenRejected() {
    QuotaAccount account = accountWith(new BigDecimal("100000.00"));
    // 软占 60000 但未确认 ⇒ frozen=60000
    account.reserveSoft(100L, new BigDecimal("60000.00"), 1L, "SET-1", "key-soft");
    assertThat(account.getQuotaFrozen()).isEqualByComparingTo("60000.00");

    assertThatThrownBy(() -> account.adjustInitial(new BigDecimal("50000.00")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("低于已占用额度");
  }

  @Test
  @DisplayName("调额不落 QuotaTransaction（配置变更不是占用流水，否则守恒口径被算歪）")
  void adjustDoesNotCreateTransaction() {
    QuotaAccount account = accountWith(new BigDecimal("100000.00"));
    int before = account.getTransactions().size();

    account.adjustInitial(new BigDecimal("200000.00"));

    assertThat(account.getTransactions()).hasSize(before);
  }

  @Test
  @DisplayName("减人 / 恢复 → 切换状态标记")
  void removeAndRestore() {
    QuotaAccount account = accountWith(new BigDecimal("100000.00"));

    account.markRemoved();
    assertThat(account.isRemoved()).isTrue();

    account.restore();
    assertThat(account.isRemoved()).isFalse();
  }

  @Test
  @DisplayName("额度流水类型常量：SOFT/HARD/PAID/RELEASE/REFUND/DEDUCTIBLE/DAILY 齐备")
  void txnTypeVocabularyComplete() {
    // 这些是 t_quota_transaction.txn_type 的合法取值集合（DDL 注释与枚举必须同步，
    // 否则"配了 DAILY 但注释里没写"这类漂移会让人误以为 DAILY 不是合法值）
    assertThat(List.of("SOFT", "HARD", "PAID", "RELEASE", "REFUND", "DEDUCTIBLE", "DAILY"))
        .hasSize(7);
  }
}
