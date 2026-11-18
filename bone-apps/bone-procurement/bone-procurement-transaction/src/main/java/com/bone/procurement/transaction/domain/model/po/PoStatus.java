package com.bone.procurement.transaction.domain.model.po;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** 采购订单状态与显式迁移表（未列出即拒绝；仅受控解锁 SENT→ACKNOWLEDGED，其余履约态无入口）。 */
public enum PoStatus {
  DRAFT,
  SUBMITTED,
  APPROVED,
  SENT,
  ACKNOWLEDGED,
  PARTIAL_RECEIVED,
  FULLY_RECEIVED,
  CLOSED,
  CANCELLED;

  private static final Map<PoStatus, Map<PoEvent, PoStatus>> TRANSITIONS;

  static {
    Map<PoStatus, Map<PoEvent, PoStatus>> t = new EnumMap<>(PoStatus.class);
    t.put(DRAFT, Map.of(PoEvent.SUBMIT, SUBMITTED, PoEvent.CANCEL, CANCELLED));
    t.put(SUBMITTED, Map.of(PoEvent.APPROVE, APPROVED, PoEvent.CANCEL, CANCELLED));
    t.put(APPROVED, Map.of(PoEvent.SEND, SENT, PoEvent.CANCEL, CANCELLED));
    // 履约切片受控解锁：SENT 只允许唯一一条 ACKNOWLEDGE → ACKNOWLEDGED（ASN 的可信上游）。
    t.put(SENT, Map.of(PoEvent.ACKNOWLEDGE, ACKNOWLEDGED));
    // 其余履约保留状态仍无推进入口（fail-closed，避免提前实现 05 收货/关闭语义）
    for (PoStatus terminal :
        new PoStatus[] {ACKNOWLEDGED, PARTIAL_RECEIVED, FULLY_RECEIVED, CLOSED, CANCELLED}) {
      t.put(terminal, Map.of());
    }
    TRANSITIONS = Collections.unmodifiableMap(t);
  }

  public static boolean canTransition(PoStatus from, PoEvent event) {
    return TRANSITIONS.getOrDefault(from, Map.of()).containsKey(event);
  }

  public static PoStatus transition(PoStatus from, PoEvent event) {
    PoStatus to = TRANSITIONS.getOrDefault(from, Map.of()).get(event);
    if (to == null) {
      throw new IllegalStateException(
          "invalid_transition: currentState="
              + from
              + ", attempted="
              + event
              + ", allowed="
              + allowedEvents(from));
    }
    return to;
  }

  public static Set<PoEvent> allowedEvents(PoStatus from) {
    Map<PoEvent, PoStatus> next = TRANSITIONS.getOrDefault(from, Map.of());
    return next.isEmpty()
        ? Collections.emptySet()
        : Collections.unmodifiableSet(EnumSet.copyOf(next.keySet()));
  }
}
