package com.bone.ailedger.infrastructure.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.ailedger.domain.gateway.LedgerAuditPort;
import com.bone.ailedger.domain.gateway.LimitLedger;
import com.bone.ailedger.domain.model.LedgerAuditRecord;
import com.bone.ailedger.domain.model.LimitScope;
import com.bone.ailedger.domain.model.LimitVerdictReason;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 额度变异审计验证（碰钱内核必须留痕）。
 *
 * <p>重点守三条：①每次变异（含被拒绝的）都有记录；②记录带前后快照、恒等式可逐笔复核； ③审计不影响业务结论（留痕失败不得改变账）。
 */
class InMemoryLimitLedgerAuditTest {

  private static final LimitScope SCOPE = LimitScope.of("LIABILITY", "clause-42");

  private final List<LedgerAuditRecord> records = new ArrayList<>();
  private final LedgerAuditPort audit = records::add;
  private LimitLedger ledger;

  @BeforeEach
  void setUp() {
    ledger = new InMemoryLimitLedger(audit);
  }

  @Test
  void every_mutation_is_audited_including_rejected_ones() {
    ledger.declareLimit(SCOPE, bd("500"));
    ledger.reserve("r1", SCOPE, bd("100"));
    ledger.reserve("r2", SCOPE, bd("900")); // 超限被拒——同样是合规关注事件
    ledger.commit("r1");

    assertThat(records)
        .extracting(LedgerAuditRecord::operation)
        .containsExactly("declareLimit", "reserve", "reserve", "commit");
    assertThat(records)
        .last()
        .satisfies(
            r -> {
              assertThat(r.operation()).isEqualTo("commit");
              assertThat(r.reservationId()).isEqualTo("r1");
              assertThat(r.amount()).isEqualByComparingTo("100");
              assertThat(r.reason()).isEqualTo(LimitVerdictReason.COMMITTED);
            });
  }

  @Test
  void rejected_reserve_is_audited_with_limit_exceeded_reason() {
    ledger.declareLimit(SCOPE, bd("500"));

    ledger.reserve("r1", SCOPE, bd("600"));

    LedgerAuditRecord r = records.get(records.size() - 1);
    assertThat(r.operation()).isEqualTo("reserve");
    assertThat(r.reason()).isEqualTo(LimitVerdictReason.LIMIT_EXCEEDED);
    // 被拒 ⇒ 前后快照一致（未动账）
    assertThat(r.before().available()).isEqualByComparingTo(r.after().available());
  }

  @Test
  void before_after_snapshots_allow_per_entry_reconciliation() {
    ledger.declareLimit(SCOPE, bd("500"));
    ledger.reserve("r1", SCOPE, bd("100"));
    ledger.commit("r1");

    // commit 这一笔：before(consumed=0,reserved=100,available=400) →
    // after(consumed=100,reserved=0,available=400)
    LedgerAuditRecord commit = records.get(records.size() - 1);
    assertThat(commit.before().consumed()).isEqualByComparingTo("0");
    assertThat(commit.before().reserved()).isEqualByComparingTo("100");
    assertThat(commit.before().available()).isEqualByComparingTo("400");
    assertThat(commit.after().consumed()).isEqualByComparingTo("100");
    assertThat(commit.after().reserved()).isEqualByComparingTo("0");
    assertThat(commit.after().available()).isEqualByComparingTo("400");
  }

  @Test
  void invalid_amount_is_not_audited_but_verdict_is_still_coded() {
    // 参数非法在进锁前就被拒（无账可变动）⇒ 不留痕；业务结论仍是编码化的
    var v = ledger.reserve("r1", SCOPE, bd("0"));

    assertThat(v.reason()).isEqualTo(LimitVerdictReason.AMOUNT_INVALID);
    assertThat(records).isEmpty();
  }

  @Test
  void no_audit_port_means_silent_but_accounting_still_correct() {
    LimitLedger noAudit = new InMemoryLimitLedger();

    noAudit.declareLimit(SCOPE, bd("500"));
    noAudit.reserve("r1", SCOPE, bd("100"));

    // 无审计端口：静默跳过留痕，但账一分不差
    assertThat(noAudit.available(SCOPE)).isEqualByComparingTo("400");
  }

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }
}
