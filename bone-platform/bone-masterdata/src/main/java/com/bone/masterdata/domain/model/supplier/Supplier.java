package com.bone.masterdata.domain.model.supplier;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Supplier 合作资格聚合根（寻源、准入、绩效、风险、退出）。
 *
 * <p><b>Owner</b>：采购。承载 Purchasing 侧的合作决策，不持有任何银行、税务、付款条件或付款冻结语义 —— 那些属于财务侧的 {@link
 * Vendor}。二者可以指向同一 partyId，但状态机互不覆盖。
 *
 * <p><b>不可变</b>：{@link #transition} 返回新实例并递增版本，历史迁移链随之增长；这样并发读取到的旧快照不会被追溯改写。
 */
public final class Supplier {

  private final long tenantId;
  private final String partyId;
  private final SupplierState state;
  private final long version;
  private final List<SupplierStateChange> history;

  private Supplier(
      long tenantId,
      String partyId,
      SupplierState state,
      long version,
      List<SupplierStateChange> history) {
    this.tenantId = tenantId;
    this.partyId = partyId;
    this.state = state;
    this.version = version;
    this.history = history;
  }

  /** 新登记的合作资格：起点固定为 {@link SupplierState#PROSPECT}。 */
  public static Supplier prospect(long tenantId, String partyId) {
    return rehydrate(tenantId, partyId, SupplierState.PROSPECT, 1L);
  }

  /** 从持久化快照重建聚合（出站端口读回时使用，不做状态迁移裁决）。 */
  public static Supplier rehydrate(
      long tenantId, String partyId, SupplierState state, long version) {
    return rehydrate(tenantId, partyId, state, version, List.of());
  }

  /** 从持久化快照重建聚合，并带回已发生的迁移链（治理端展示与事后仲裁使用）。 */
  public static Supplier rehydrate(
      long tenantId,
      String partyId,
      SupplierState state,
      long version,
      List<SupplierStateChange> history) {
    if (partyId == null || partyId.trim().isEmpty()) {
      throw new DomainException("合作资格缺少 partyId，禁止构造");
    }
    if (state == null) {
      throw new DomainException("合作资格缺少生命周期状态，禁止构造");
    }
    if (version < 0L) {
      throw new DomainException("合作资格版本不得为负: " + version);
    }
    return new Supplier(
        tenantId,
        partyId.trim(),
        state,
        version,
        history == null ? List.of() : List.copyOf(history));
  }

  /**
   * 推进合作资格状态。
   *
   * @param verdict 守卫结论；需要守卫的迁移必须提供，缺失或拒绝即抛，状态与历史均不变
   * @param operator 操作人账号，必填（迁移必须可追溯）
   * @param at 迁移时间，必填
   * @return 迁移后的新聚合实例
   */
  public Supplier transition(
      SupplierEvent event, GuardVerdict verdict, String operator, Instant at) {
    if (event == null) {
      throw new DomainException("迁移事件不能为空");
    }
    if (operator == null || operator.trim().isEmpty()) {
      throw new DomainException("状态迁移必须记录操作人");
    }
    if (at == null) {
      throw new DomainException("状态迁移必须记录时间");
    }
    SupplierState next = SupplierLifecycle.apply(state, event, verdict);
    List<SupplierStateChange> nextHistory = new ArrayList<>(history);
    nextHistory.add(
        new SupplierStateChange(
            state,
            event,
            next,
            verdict == null ? null : verdict.reasonCode(),
            operator.trim(),
            at));
    return new Supplier(tenantId, partyId, next, version + 1L, List.copyOf(nextHistory));
  }

  public long tenantId() {
    return tenantId;
  }

  public String partyId() {
    return partyId;
  }

  public SupplierState state() {
    return state;
  }

  public long version() {
    return version;
  }

  public List<SupplierStateChange> history() {
    return history;
  }
}
