package com.bone.masterdata.domain.model.supplier;

import com.bone.core.exception.DomainException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vendor 交易与付款资格迁移表（`Map<State, Map<Event, Transition>>`，LLD `01 卷 §5`）。
 *
 * <p><b>Owner 边界由表结构表达</b>：{@link VendorState#INACTIVE_VENDOR} 无任何 from 行 —— 重新开通必须新建一轮闸门，不存在
 * {@code INACTIVE_VENDOR → ACTIVE_VENDOR} 的直通路径。
 *
 * <p><b>守卫分布</b>：开通闸门与停用前置都依赖跨域事实（准入/银行/税务/付款条件/重复发票、进行中采购），在表中标 guarded 并由守卫端口裁决；
 * 付款冻结的「必须来自财务事件」约束由聚合层的命令来源校验保证（设计 D7），不在表里重复表达。
 */
public final class VendorLifecycle {

  private static final boolean GUARDED = true;
  private static final boolean UNGUARDED = false;

  private static final Map<VendorState, Map<VendorEvent, VendorTransition>> TABLE = build();

  private VendorLifecycle() {}

  private static Map<VendorState, Map<VendorEvent, VendorTransition>> build() {
    Map<VendorState, Map<VendorEvent, VendorTransition>> table = new EnumMap<>(VendorState.class);
    add(
        table,
        VendorState.PENDING_VENDOR,
        VendorEvent.ACTIVATION_PASSED,
        VendorState.ACTIVE_VENDOR,
        GUARDED);
    add(
        table,
        VendorState.ACTIVE_VENDOR,
        VendorEvent.PAYMENT_BLOCK_RAISED,
        VendorState.PAYMENT_BLOCKED,
        UNGUARDED);
    add(
        table,
        VendorState.PAYMENT_BLOCKED,
        VendorEvent.PAYMENT_BLOCK_RELEASED,
        VendorState.ACTIVE_VENDOR,
        UNGUARDED);
    add(
        table,
        VendorState.PENDING_VENDOR,
        VendorEvent.DEACTIVATE,
        VendorState.INACTIVE_VENDOR,
        GUARDED);
    add(
        table,
        VendorState.ACTIVE_VENDOR,
        VendorEvent.DEACTIVATE,
        VendorState.INACTIVE_VENDOR,
        GUARDED);
    add(
        table,
        VendorState.PAYMENT_BLOCKED,
        VendorEvent.DEACTIVATE,
        VendorState.INACTIVE_VENDOR,
        GUARDED);
    return Collections.unmodifiableMap(table);
  }

  private static void add(
      Map<VendorState, Map<VendorEvent, VendorTransition>> table,
      VendorState from,
      VendorEvent event,
      VendorState to,
      boolean guarded) {
    table
        .computeIfAbsent(from, key -> new EnumMap<>(VendorEvent.class))
        .put(event, new VendorTransition(from, event, to, guarded));
  }

  /** 解析迁移；不存在则抛，消息携带当前状态、被拒事件与当前允许事件集。 */
  public static VendorTransition resolve(VendorState from, VendorEvent event) {
    if (from == null || event == null) {
      throw new DomainException("状态迁移缺少起始状态或事件");
    }
    VendorTransition transition = table(from).get(event);
    if (transition == null) {
      throw new DomainException(
          "非法迁移：当前状态 "
              + from.name()
              + " 不接受事件 "
              + event.code()
              + "；当前允许的事件 "
              + allowedEvents(from).stream().map(VendorEvent::code).toList());
    }
    return transition;
  }

  /** 执行迁移并返回目标状态；guarded 迁移缺守卫结论或结论为拒绝时抛出。 */
  public static VendorState apply(VendorState from, VendorEvent event, GuardVerdict verdict) {
    VendorTransition transition = resolve(from, event);
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

  /** 当前状态允许的事件集（终态返回空集）。 */
  public static Set<VendorEvent> allowedEvents(VendorState from) {
    return table(from).keySet().stream()
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(VendorEvent.class)));
  }

  private static Map<VendorEvent, VendorTransition> table(VendorState from) {
    return TABLE.getOrDefault(from, Map.of());
  }
}
