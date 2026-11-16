package com.bone.masterdata.domain.model.supplier;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Vendor 交易与付款资格聚合（财务侧 Company Code data：银行、税务、付款条件、付款冻结）。
 *
 * <p><b>Owner</b>：财务。采购域可以推进普通迁移，但不得擅自解除付款冻结，也不得在未跑完交易付款闸门的情况下置为已开通。
 *
 * <p>与 {@link Supplier} 是同一 partyId 下的两个独立聚合，各自持有互不覆盖的状态机。
 */
public final class Vendor {

  private final long tenantId;
  private final String partyId;
  private final VendorState state;
  private final long version;
  private final List<VendorStateChange> history;

  private Vendor(
      long tenantId,
      String partyId,
      VendorState state,
      long version,
      List<VendorStateChange> history) {
    this.tenantId = tenantId;
    this.partyId = partyId;
    this.state = state;
    this.version = version;
    this.history = history;
  }

  /** 新登记的交易付款资格：起点固定为 {@link VendorState#PENDING_VENDOR}，须跑完闸门才可开通。 */
  public static Vendor pending(long tenantId, String partyId) {
    return rehydrate(tenantId, partyId, VendorState.PENDING_VENDOR, 1L);
  }

  /** 从持久化快照重建聚合（出站端口读回时使用，不做状态迁移裁决）。 */
  public static Vendor rehydrate(long tenantId, String partyId, VendorState state, long version) {
    return rehydrate(tenantId, partyId, state, version, List.of());
  }

  /** 从持久化快照重建聚合，并带回已发生的迁移链。 */
  public static Vendor rehydrate(
      long tenantId,
      String partyId,
      VendorState state,
      long version,
      List<VendorStateChange> history) {
    if (partyId == null || partyId.trim().isEmpty()) {
      throw new DomainException("交易付款资格缺少 partyId，禁止构造");
    }
    if (state == null) {
      throw new DomainException("交易付款资格缺少生命周期状态，禁止构造");
    }
    if (version < 0L) {
      throw new DomainException("交易付款资格版本不得为负: " + version);
    }
    return new Vendor(
        tenantId,
        partyId.trim(),
        state,
        version,
        history == null ? List.of() : List.copyOf(history));
  }

  /**
   * 推进交易付款资格状态。
   *
   * @param source 命令来源；付款冻结的<b>提起与解除</b>都只接受 {@link TransitionCommandSource#EXTERNAL_FINANCE}（设计
   *     D7：本域不得自行冻结也不得自行放行），域内来源一律拒绝
   * @param verdict 守卫结论；需要守卫的迁移必须提供
   * @param operator 操作人账号，必填
   * @param at 迁移时间，必填
   * @param manualReviewTraced 解除付款冻结时是否存在人工复核留痕；仅对 {@link VendorEvent#PAYMENT_BLOCK_RELEASED} 生效
   * @return 迁移后的新聚合实例
   */
  public Vendor transition(
      VendorEvent event,
      TransitionCommandSource source,
      GuardVerdict verdict,
      String operator,
      Instant at,
      boolean manualReviewTraced) {
    if (event == null) {
      throw new DomainException("迁移事件不能为空");
    }
    if (source == null) {
      throw new DomainException("状态迁移必须声明命令来源");
    }
    if (requiresExternalFinance(event) && source != TransitionCommandSource.EXTERNAL_FINANCE) {
      throw new DomainException(
          "付款冻结的提起与解除都只能由外部财务事件发起，域内来源一律拒绝（" + event.code() + " / " + source.name() + "）");
    }
    if (event == VendorEvent.PAYMENT_BLOCK_RELEASED && !manualReviewTraced) {
      // spec:83 要求「外部财务事件 + 人工复核留痕」两个条件同时成立；只校验来源等于放过一半条件。
      throw new DomainException("付款冻结解除缺少人工复核留痕，禁止放行");
    }
    if (operator == null || operator.trim().isEmpty()) {
      throw new DomainException("状态迁移必须记录操作人");
    }
    if (at == null) {
      throw new DomainException("状态迁移必须记录时间");
    }
    VendorState next = VendorLifecycle.apply(state, event, verdict);
    List<VendorStateChange> nextHistory = new ArrayList<>(history);
    nextHistory.add(
        new VendorStateChange(
            state,
            event,
            next,
            source,
            verdict == null ? null : verdict.reasonCode(),
            operator.trim(),
            at));
    return new Vendor(tenantId, partyId, next, version + 1L, List.copyOf(nextHistory));
  }

  public long tenantId() {
    return tenantId;
  }

  public String partyId() {
    return partyId;
  }

  public VendorState state() {
    return state;
  }

  public long version() {
    return version;
  }

  public List<VendorStateChange> history() {
    return history;
  }

  /** 付款冻结的两个事件都属外部财务事件（D7 覆盖提起与解除，不只是解除）。 */
  private static boolean requiresExternalFinance(VendorEvent event) {
    return event == VendorEvent.PAYMENT_BLOCK_RAISED || event == VendorEvent.PAYMENT_BLOCK_RELEASED;
  }
}
