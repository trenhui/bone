package com.bone.ailedger.infrastructure.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ailedger.domain.gateway.LimitLedger;
import com.bone.ailedger.domain.model.LimitScope;
import com.bone.ailedger.domain.model.LimitVerdict;
import com.bone.ailedger.domain.model.LimitVerdictReason;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryLimitLedgerTest {

  private static final LimitScope LIABILITY = LimitScope.of("LIABILITY", "clause-42");
  private static final LimitScope PERSONAL = LimitScope.of("PERSONAL", "user-1001");

  private LimitLedger ledger;

  @BeforeEach
  void setUp() {
    ledger = new InMemoryLimitLedger();
  }

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  @Test
  void undeclared_scope_has_zero_available_fail_closed() {
    // 没配上限就不放行：fail-closed，而非「无限制」
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(ledger.reserve("r1", LIABILITY, bd("1")).ok()).isFalse();
  }

  @Test
  void reserve_within_limit_reduces_available() {
    ledger.declareLimit(LIABILITY, bd("500"));

    LimitVerdict v = ledger.reserve("r1", LIABILITY, bd("100"));

    assertThat(v.ok()).isTrue();
    assertThat(v.reason()).isEqualTo(LimitVerdictReason.RESERVED);
    assertThat(v.reservation().isReserved()).isTrue();
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("400"));
  }

  @Test
  void reserve_beyond_limit_is_rejected_with_coded_reason() {
    ledger.declareLimit(LIABILITY, bd("500"));

    LimitVerdict v = ledger.reserve("r1", LIABILITY, bd("600"));

    assertThat(v.ok()).isFalse();
    assertThat(v.reason()).isEqualTo(LimitVerdictReason.LIMIT_EXCEEDED);
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("500")); // 未动账
  }

  @Test
  void commit_moves_reserved_to_consumed_keeping_available() {
    ledger.declareLimit(LIABILITY, bd("500"));
    ledger.reserve("r1", LIABILITY, bd("100"));

    LimitVerdict v = ledger.commit("r1");

    assertThat(v.reason()).isEqualTo(LimitVerdictReason.COMMITTED);
    // 提交后：已占用 100、可用仍 400（额度确实被用掉，只是从「占位」转为「实占」）
    assertThat(ledger.snapshot(LIABILITY).consumed()).isEqualByComparingTo(bd("100"));
    assertThat(ledger.snapshot(LIABILITY).reserved()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("400"));
  }

  @Test
  void release_returns_amount_to_available() {
    ledger.declareLimit(LIABILITY, bd("500"));
    ledger.reserve("r1", LIABILITY, bd("100"));

    LimitVerdict v = ledger.release("r1");

    assertThat(v.reason()).isEqualTo(LimitVerdictReason.RELEASED);
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("500")); // 全额返还
    assertThat(ledger.snapshot(LIABILITY).consumed()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void double_settle_is_rejected_not_double_counted() {
    ledger.declareLimit(LIABILITY, bd("500"));
    ledger.reserve("r1", LIABILITY, bd("100"));
    ledger.commit("r1");

    LimitVerdict again = ledger.commit("r1");

    assertThat(again.ok()).isFalse();
    assertThat(again.reason()).isEqualTo(LimitVerdictReason.RESERVATION_NOT_FOUND);
    // 关键：不允许二次扣账
    assertThat(ledger.snapshot(LIABILITY).consumed()).isEqualByComparingTo(bd("100"));
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("400"));
  }

  @Test
  void settle_unknown_reservation_is_rejected() {
    assertThat(ledger.commit("nope").reason()).isEqualTo(LimitVerdictReason.RESERVATION_NOT_FOUND);
    assertThat(ledger.release("nope").reason()).isEqualTo(LimitVerdictReason.RESERVATION_NOT_FOUND);
  }

  @Test
  void reserve_is_idempotent_for_same_id_scope_and_amount() {
    ledger.declareLimit(LIABILITY, bd("500"));

    LimitVerdict first = ledger.reserve("r1", LIABILITY, bd("100"));
    LimitVerdict retry = ledger.reserve("r1", LIABILITY, bd("100"));

    assertThat(retry.reason()).isEqualTo(LimitVerdictReason.RESERVED);
    // 幂等：重复预留不重复占额
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("400"));
    assertThat(first.reservation().id()).isEqualTo(retry.reservation().id());
  }

  @Test
  void reserve_id_conflict_is_rejected() {
    ledger.declareLimit(LIABILITY, bd("500"));
    ledger.reserve("r1", LIABILITY, bd("100"));

    LimitVerdict conflict = ledger.reserve("r1", LIABILITY, bd("200"));

    assertThat(conflict.ok()).isFalse();
    assertThat(conflict.reason()).isEqualTo(LimitVerdictReason.RESERVATION_NOT_FOUND);
  }

  @Test
  void freeze_reduces_available_and_thaw_restores() {
    ledger.declareLimit(LIABILITY, bd("500"));

    assertThat(ledger.freeze(LIABILITY, bd("150")).reason()).isEqualTo(LimitVerdictReason.FROZEN);
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("350"));
    assertThat(ledger.snapshot(LIABILITY).frozen()).isEqualByComparingTo(bd("150"));

    assertThat(ledger.thaw(LIABILITY, bd("150")).reason()).isEqualTo(LimitVerdictReason.THAWED);
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("500"));
  }

  @Test
  void freeze_beyond_available_and_thaw_beyond_frozen_are_rejected() {
    ledger.declareLimit(LIABILITY, bd("500"));

    assertThat(ledger.freeze(LIABILITY, bd("600")).reason())
        .isEqualTo(LimitVerdictReason.LIMIT_EXCEEDED);
    assertThat(ledger.thaw(LIABILITY, bd("1")).reason())
        .isEqualTo(LimitVerdictReason.LIMIT_EXCEEDED);
  }

  @Test
  void reserved_and_frozen_both_reduce_available() {
    ledger.declareLimit(LIABILITY, bd("500"));
    ledger.reserve("r1", LIABILITY, bd("100"));
    ledger.freeze(LIABILITY, bd("50"));

    // available = 500 - 0(consumed) - 100(reserved) - 50(frozen) = 350
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("350"));
  }

  @Test
  void different_scopes_are_isolated() {
    ledger.declareLimit(LIABILITY, bd("500"));
    ledger.declareLimit(PERSONAL, bd("1000"));
    ledger.reserve("r1", LIABILITY, bd("400"));

    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(bd("100"));
    assertThat(ledger.available(PERSONAL)).isEqualByComparingTo(bd("1000")); // 不受另一维度影响
  }

  @Test
  void invalid_amounts_and_ids_are_rejected() {
    ledger.declareLimit(LIABILITY, bd("500"));
    assertThat(ledger.reserve("r1", LIABILITY, bd("0")).reason())
        .isEqualTo(LimitVerdictReason.AMOUNT_INVALID);
    assertThat(ledger.reserve("r1", LIABILITY, bd("-5")).reason())
        .isEqualTo(LimitVerdictReason.AMOUNT_INVALID);
    assertThat(ledger.reserve(null, LIABILITY, bd("5")).reason())
        .isEqualTo(LimitVerdictReason.AMOUNT_INVALID);
    assertThat(ledger.reserve("  ", LIABILITY, bd("5")).reason())
        .isEqualTo(LimitVerdictReason.AMOUNT_INVALID);
  }

  @Test
  void concurrent_reserves_never_exceed_limit() throws InterruptedException {
    ledger.declareLimit(LIABILITY, bd("1000"));
    int threads = 16;
    int perThread = 20;
    var latch = new java.util.concurrent.CountDownLatch(1);
    var done = new java.util.concurrent.CountDownLatch(threads);
    var successes = new java.util.concurrent.atomic.AtomicInteger();
    for (int t = 0; t < threads; t++) {
      final int idx = t;
      new Thread(
              () -> {
                try {
                  latch.await();
                  for (int i = 0; i < perThread; i++) {
                    if (ledger.reserve("r-" + idx + "-" + i, LIABILITY, bd("10")).ok()) {
                      successes.incrementAndGet();
                    }
                  }
                } catch (InterruptedException ignored) {
                  Thread.currentThread().interrupt();
                } finally {
                  done.countDown();
                }
              })
          .start();
    }
    latch.countDown();
    done.await();

    // 1000 / 10 = 100 笔成功，绝不多出一笔（并发下无超卖）
    assertThat(successes.get()).isEqualTo(100);
    assertThat(ledger.snapshot(LIABILITY).reserved()).isEqualByComparingTo(bd("1000"));
    assertThat(ledger.available(LIABILITY)).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
