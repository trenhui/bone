package com.bone.procurement.transaction.domain.model.po;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** 采购订单状态与显式迁移表（未列出即拒绝；SENT 之后的履约状态本期不提供入口）。 */
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
    // SENT 及履约保留状态：本期无推进入口（fail-closed，避免提前实现 05 履约语义）
    for (PoStatus terminal :
        new PoStatus[] {SENT, ACKNOWLEDGED, PARTIAL_RECEIVED, FULLY_RECEIVED, CLOSED, CANCELLED}) {
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
