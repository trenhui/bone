package com.bone.ailedger.infrastructure.ledger;

import com.bone.ailedger.domain.gateway.LedgerAuditPort;
import com.bone.ailedger.domain.gateway.LimitLedger;
import com.bone.ailedger.domain.model.LedgerAuditRecord;
import com.bone.ailedger.domain.model.LimitScope;
import com.bone.ailedger.domain.model.LimitSnapshot;
import com.bone.ailedger.domain.model.LimitVerdict;
import com.bone.ailedger.domain.model.LimitVerdictReason;
import com.bone.ailedger.domain.model.Reservation;
import com.bone.ailedger.domain.model.ReservationState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版多维额度台账（默认实现，进程内正确）。
 *
 * <p><b>并发模型</b>：每个维度范围一个 {@link ScopeState}，全部金额变更在「该范围的锁」内完成—— 不同范围可并行，同一范围串行， 保证 {@code
 * available = limit - consumed - reserved - frozen} 恒等式不被并发破坏。预留表的读写也在所属范围的锁内， 因此 「查重 → 扣可用额 →
 * 落预留」三步原子，杜绝并发双花。
 *
 * <p><b>每次变异必留痕</b>：所有操作（含被拒绝的，如超限 reserve）都在同一把锁内取「操作前 / 操作后」快照并 emit 一条 {@link
 * LedgerAuditRecord}——碰钱内核没有审计就不可对账、不可溯源。审计端口可空（无审计时静默跳过，便于纯计算场景）。
 *
 * <p><b>进程内状态</b>：多副本各记各的（与 bone-ai-gateway 的 {@code TenantQuotaGuard} 同理）。 需全局一致以 {@code @Bean
 * LimitLedger} 覆盖为共享存储实现（Redis 等，L3）；端口不变，调用方零改动。
 */
public class InMemoryLimitLedger implements LimitLedger {

  private final Map<String, ScopeState> scopes = new ConcurrentHashMap<>();
  private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
  private final LedgerAuditPort audit;

  public InMemoryLimitLedger() {
    this(null);
  }

  public InMemoryLimitLedger(LedgerAuditPort audit) {
    this.audit = audit;
  }

  @Override
  public LimitVerdict declareLimit(LimitScope scope, BigDecimal limit) {
    if (limit == null || limit.signum() < 0) {
      return LimitVerdict.of(LimitVerdictReason.AMOUNT_INVALID, "限额上限不可为负: " + limit);
    }
    ScopeState st = stateOf(scope);
    synchronized (st) {
      LimitSnapshot before = snapshotLocked(scope, st);
      st.limit = limit;
      LimitVerdict verdict =
          LimitVerdict.of(
              LimitVerdictReason.LIMIT_DECLARED, scope.dimension() + "/" + scope.scopeKey());
      audit(
          scope, "declareLimit", null, limit, verdict.reason(), before, snapshotLocked(scope, st));
      return verdict;
    }
  }

  @Override
  public LimitVerdict reserve(String reservationId, LimitScope scope, BigDecimal amount) {
    if (reservationId == null || reservationId.isBlank()) {
      return LimitVerdict.of(LimitVerdictReason.AMOUNT_INVALID, "预留标识不可为空");
    }
    if (amount == null || amount.signum() <= 0) {
      return LimitVerdict.of(LimitVerdictReason.AMOUNT_INVALID, "预留金额须为正数: " + amount);
    }
    ScopeState st = stateOf(scope);
    synchronized (st) {
      LimitSnapshot before = snapshotLocked(scope, st);
      LimitVerdict verdict = doReserve(reservationId, scope, amount, st);
      audit(
          scope,
          "reserve",
          reservationId,
          amount,
          verdict.reason(),
          before,
          snapshotLocked(scope, st));
      return verdict;
    }
  }

  @Override
  public LimitVerdict commit(String reservationId) {
    return settle(
        reservationId, ReservationState.CONSUMED, LimitVerdictReason.COMMITTED, "commit", "提交");
  }

  @Override
  public LimitVerdict release(String reservationId) {
    return settle(
        reservationId, ReservationState.RELEASED, LimitVerdictReason.RELEASED, "release", "释放");
  }

  @Override
  public LimitVerdict freeze(LimitScope scope, BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      return LimitVerdict.of(LimitVerdictReason.AMOUNT_INVALID, "冻结金额须为正数: " + amount);
    }
    ScopeState st = stateOf(scope);
    synchronized (st) {
      LimitSnapshot before = snapshotLocked(scope, st);
      LimitVerdict verdict;
      BigDecimal available = st.available();
      if (available.compareTo(amount) < 0) {
        verdict =
            LimitVerdict.of(
                LimitVerdictReason.LIMIT_EXCEEDED,
                "冻结超出可用额度: available=" + available + " amount=" + amount,
                null);
      } else {
        st.frozen = st.frozen.add(amount);
        verdict = LimitVerdict.of(LimitVerdictReason.FROZEN, "冻结成功");
      }
      audit(scope, "freeze", null, amount, verdict.reason(), before, snapshotLocked(scope, st));
      return verdict;
    }
  }

  @Override
  public LimitVerdict thaw(LimitScope scope, BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      return LimitVerdict.of(LimitVerdictReason.AMOUNT_INVALID, "解冻金额须为正数: " + amount);
    }
    ScopeState st = stateOf(scope);
    synchronized (st) {
      LimitSnapshot before = snapshotLocked(scope, st);
      LimitVerdict verdict;
      if (st.frozen.compareTo(amount) < 0) {
        verdict =
            LimitVerdict.of(
                LimitVerdictReason.LIMIT_EXCEEDED,
                "解冻超出已冻结额度: frozen=" + st.frozen + " amount=" + amount,
                null);
      } else {
        st.frozen = st.frozen.subtract(amount);
        verdict = LimitVerdict.of(LimitVerdictReason.THAWED, "解冻成功");
      }
      audit(scope, "thaw", null, amount, verdict.reason(), before, snapshotLocked(scope, st));
      return verdict;
    }
  }

  @Override
  public BigDecimal available(LimitScope scope) {
    ScopeState st = scopes.get(keyOf(scope));
    if (st == null) {
      return BigDecimal.ZERO;
    }
    synchronized (st) {
      return st.available();
    }
  }

  @Override
  public LimitSnapshot snapshot(LimitScope scope) {
    ScopeState st = scopes.get(keyOf(scope));
    if (st == null) {
      BigDecimal zero = BigDecimal.ZERO;
      return new LimitSnapshot(scope, zero, zero, zero, zero, zero);
    }
    synchronized (st) {
      return snapshotLocked(scope, st);
    }
  }

  // ---- internals ----

  /** reserve 的锁内主体（幂等判定 → 额度判定 → 落预留）。 */
  private LimitVerdict doReserve(
      String reservationId, LimitScope scope, BigDecimal amount, ScopeState st) {
    Reservation existing = reservations.get(reservationId);
    if (existing != null) {
      // 幂等：同一标识 + 同范围 + 同金额的重试，返回已存在的预留（重试 / 补偿队列安全重放）
      if (existing.isReserved()
          && existing.scope().equals(scope)
          && existing.amount().compareTo(amount) == 0) {
        return LimitVerdict.of(LimitVerdictReason.RESERVED, "幂等：相同预留已存在", existing);
      }
      return LimitVerdict.of(
          LimitVerdictReason.RESERVATION_NOT_FOUND, "预留标识已存在且已结算或参数不符: " + reservationId, null);
    }
    BigDecimal available = st.available();
    if (available.compareTo(amount) < 0) {
      return LimitVerdict.of(
          LimitVerdictReason.LIMIT_EXCEEDED,
          "scope="
              + scope.dimension()
              + "/"
              + scope.scopeKey()
              + " available="
              + available
              + " amount="
              + amount,
          null);
    }
    st.reserved = st.reserved.add(amount);
    Reservation created = new Reservation(reservationId, scope, amount, ReservationState.RESERVED);
    reservations.put(reservationId, created);
    return LimitVerdict.of(LimitVerdictReason.RESERVED, "预留成功", created);
  }

  /** 结算（commit / release 共用）：必须在预留所属范围的锁内，保证「预留表」与「范围金额」一致变更。 */
  private LimitVerdict settle(
      String reservationId,
      ReservationState target,
      LimitVerdictReason reason,
      String operation,
      String verb) {
    if (reservationId == null || reservationId.isBlank()) {
      return LimitVerdict.of(LimitVerdictReason.AMOUNT_INVALID, "预留标识不可为空");
    }
    Reservation current = reservations.get(reservationId);
    if (current == null || !current.isReserved()) {
      return LimitVerdict.of(
          LimitVerdictReason.RESERVATION_NOT_FOUND, "预留不存在或已结算: " + reservationId, null);
    }
    ScopeState st = stateOf(current.scope());
    synchronized (st) {
      LimitSnapshot before = snapshotLocked(current.scope(), st);
      // 锁内复核：拿到锁后状态可能已被他人推进
      Reservation locked = reservations.get(reservationId);
      if (locked == null || !locked.isReserved()) {
        return LimitVerdict.of(
            LimitVerdictReason.RESERVATION_NOT_FOUND, "预留不存在或已结算: " + reservationId, null);
      }
      st.reserved = st.reserved.subtract(locked.amount());
      if (target == ReservationState.CONSUMED) {
        st.consumed = st.consumed.add(locked.amount());
      }
      Reservation settled = new Reservation(reservationId, locked.scope(), locked.amount(), target);
      reservations.put(reservationId, settled);
      LimitVerdict verdict = LimitVerdict.of(reason, verb + "成功", settled);
      audit(
          current.scope(),
          operation,
          reservationId,
          locked.amount(),
          verdict.reason(),
          before,
          snapshotLocked(current.scope(), st));
      return verdict;
    }
  }

  private ScopeState stateOf(LimitScope scope) {
    return scopes.computeIfAbsent(keyOf(scope), k -> new ScopeState());
  }

  private static String keyOf(LimitScope scope) {
    return scope.dimension() + "\u0000" + scope.scopeKey();
  }

  /** 锁内快照（调用方必须已持有该 ScopeState 的锁）。 */
  private static LimitSnapshot snapshotLocked(LimitScope scope, ScopeState st) {
    return new LimitSnapshot(scope, st.limit, st.consumed, st.reserved, st.frozen, st.available());
  }

  /** 留痕：审计端口为空时静默跳过（纯计算场景不强制留痕）。 */
  private void audit(
      LimitScope scope,
      String operation,
      String reservationId,
      BigDecimal amount,
      LimitVerdictReason reason,
      LimitSnapshot before,
      LimitSnapshot after) {
    if (audit == null) {
      return;
    }
    audit.record(
        new LedgerAuditRecord(
            scope, operation, reservationId, amount, reason, before, after, Instant.now()));
  }

  /** 单范围的记账状态（全部字段的变更都在 {@code synchronized(this)} 内）。 */
  private static final class ScopeState {
    private BigDecimal limit = BigDecimal.ZERO;
    private BigDecimal consumed = BigDecimal.ZERO;
    private BigDecimal reserved = BigDecimal.ZERO;
    private BigDecimal frozen = BigDecimal.ZERO;

    private BigDecimal available() {
      return limit.subtract(consumed).subtract(reserved).subtract(frozen);
    }
  }
}
