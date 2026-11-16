package com.bone.masterdata.domain.model.supplier;

import com.bone.core.exception.DomainException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Supplier 合作资格迁移表（`Map<State, Map<Event, Transition>>`，LLD `01 卷 §5` 明文要求）。
 *
 * <p><b>fail-closed 由表结构本身保证</b>：
 *
 * <ul>
 *   <li>表中未列出的 (state, event) 组合一律拒绝，并回传当前允许的事件集；
 *   <li>{@link SupplierState#EXITED} 无任何 from 行 —— 终态不可回写不靠额外判断，而是「表里没有」；
 *   <li>守卫集中在 domain：需要守卫的迁移在表中标记 guarded，调用方未提供结论即拒绝（不允许默认放行）。
 * </ul>
 *
 * <p><b>哪些迁移设守卫、为什么</b>（2026-11-16 独立审查 C-03 修复）：只有「准入」与「退出」两个迁移设守卫，其余（提交准入、风险暂停、限期整改、 恢复）不设守卫 ——
 * 那些事件本身就是上游流程的结论（例如「风险事件已发生」），本域再设一道守卫只能靠伪造结论才能推进。
 *
 * <p>准入守卫的依据是 spec:54 明确要求的两项事实：「变更请求全段已批准」与「至少存在 1 个 {@code ACTIVE_VENDOR}」。此前该迁移被标为无需守卫，
 * 等于「收到事件就批」，与 spec 冲突；两项事实中前者来自变更请求域端口、后者由本域直接点算，都不允许调用方自报， 因此不存在「为推进而伪造 {@code pass()}」的问题。
 *
 * <p><b>为什么 RISK_EXCEEDED 与 CORRECTIVE_REQUIRED 拆成两个事件</b>：设计 D5 的语义为「风险事件 → SUSPENDED 或
 * CORRECTIVE」。显式迁移表要求 (state, event) 唯一确定目标状态，一个事件两个目标会让裁决不确定；因此以两个事件分别表达 「暂停」与「限期整改」两条不同的业务处置。
 */
public final class SupplierLifecycle {

  private static final boolean GUARDED = true;
  private static final boolean UNGUARDED = false;

  private static final Map<SupplierState, Map<SupplierEvent, SupplierTransition>> TABLE = build();

  private SupplierLifecycle() {}

  private static Map<SupplierState, Map<SupplierEvent, SupplierTransition>> build() {
    Map<SupplierState, Map<SupplierEvent, SupplierTransition>> table =
        new EnumMap<>(SupplierState.class);
    add(
        table,
        SupplierState.PROSPECT,
        SupplierEvent.ONBOARDING_SUBMITTED,
        SupplierState.ONBOARDING,
        UNGUARDED);
    add(
        table,
        SupplierState.ONBOARDING,
        SupplierEvent.ONBOARDING_APPROVED,
        SupplierState.ACTIVE,
        GUARDED);
    add(
        table,
        SupplierState.ACTIVE,
        SupplierEvent.RISK_EXCEEDED,
        SupplierState.SUSPENDED,
        UNGUARDED);
    add(
        table,
        SupplierState.ACTIVE,
        SupplierEvent.CORRECTIVE_REQUIRED,
        SupplierState.CORRECTIVE,
        UNGUARDED);
    add(table, SupplierState.SUSPENDED, SupplierEvent.RESTORED, SupplierState.ACTIVE, UNGUARDED);
    add(table, SupplierState.CORRECTIVE, SupplierEvent.RESTORED, SupplierState.ACTIVE, UNGUARDED);
    add(table, SupplierState.ACTIVE, SupplierEvent.EXIT_APPROVED, SupplierState.EXITED, GUARDED);
    add(
        table,
        SupplierState.CORRECTIVE,
        SupplierEvent.EXIT_APPROVED,
        SupplierState.EXITED,
        GUARDED);
    return Collections.unmodifiableMap(table);
  }

  private static void add(
      Map<SupplierState, Map<SupplierEvent, SupplierTransition>> table,
      SupplierState from,
      SupplierEvent event,
      SupplierState to,
      boolean guarded) {
    table
        .computeIfAbsent(from, key -> new EnumMap<>(SupplierEvent.class))
        .put(event, new SupplierTransition(from, event, to, guarded));
  }

  /** 解析迁移；不存在则抛，消息携带当前状态、被拒事件与当前允许事件集。 */
  public static SupplierTransition resolve(SupplierState from, SupplierEvent event) {
    if (from == null || event == null) {
      throw new DomainException("状态迁移缺少起始状态或事件");
    }
    SupplierTransition transition = table(from).get(event);
    if (transition == null) {
      throw new DomainException(
          "非法迁移：当前状态 "
              + from.name()
              + " 不接受事件 "
              + event.code()
              + "；当前允许的事件 "
              + allowedEvents(from).stream().map(SupplierEvent::code).toList());
    }
    return transition;
  }

  /**
   * 执行迁移并返回目标状态。
   *
   * @param verdict 守卫结论；仅 guarded 迁移必需。guarded 迁移缺结论或结论为拒绝时抛出，状态保持不变。
   */
  public static SupplierState apply(SupplierState from, SupplierEvent event, GuardVerdict verdict) {
    SupplierTransition transition = resolve(from, event);
    if (transition.guarded()) {
      if (verdict == null) {
        throw new DomainException(
            "迁移 "
                + from.name()
                + " --"
                + event.code()
                + "--> "
                + transition.to().name()
                + " 需要守卫结论，缺失即拒绝（不允许默认放行）");
      }
      if (verdict.rejected()) {
        throw new DomainException("迁移守卫拒绝：" + event.code() + "（" + verdict.reasonCode() + "）");
      }
    }
    return transition.to();
  }

  /** 当前状态允许的事件集（终态返回空集，供拒绝消息与治理端展示）。 */
  public static Set<SupplierEvent> allowedEvents(SupplierState from) {
    return table(from).keySet().stream()
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(SupplierEvent.class)));
  }

  private static Map<SupplierEvent, SupplierTransition> table(SupplierState from) {
    return TABLE.getOrDefault(from, Map.of());
  }
}
