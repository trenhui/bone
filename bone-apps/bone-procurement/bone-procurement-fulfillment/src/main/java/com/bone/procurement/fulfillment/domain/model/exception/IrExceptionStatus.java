package com.bone.procurement.fulfillment.domain.model.exception;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * IR 例外状态与显式迁移表（LLD 05 卷 §5）。
 *
 * <p>RESOLVED/REJECTED 只能由 Finance 事件回写（application 专用方法），不在通用迁移表暴露 resolve 事件； 采购域补料只到
 * SUPPLEMENTED，无放行能力。
 */
public enum IrExceptionStatus {
  OPEN,
  SUPPLEMENTED,
  UNDER_REVIEW,
  RESOLVED,
  REJECTED;

  public enum Event {
    supplement,
    under_review
  }

  private static final Map<IrExceptionStatus, Map<Event, IrExceptionStatus>> TRANSITIONS;

  static {
    Map<IrExceptionStatus, Map<Event, IrExceptionStatus>> t =
        new EnumMap<>(IrExceptionStatus.class);
    t.put(OPEN, Map.of(Event.supplement, SUPPLEMENTED));
    t.put(SUPPLEMENTED, Map.of(Event.under_review, UNDER_REVIEW));
    // UNDER_REVIEW 的 RESOLVED/REJECTED 只能由 Finance 事件回写，不在此暴露
    for (IrExceptionStatus terminal : new IrExceptionStatus[] {UNDER_REVIEW, RESOLVED, REJECTED}) {
      t.putIfAbsent(terminal, Map.of());
    }
    TRANSITIONS = Collections.unmodifiableMap(t);
  }

  public static boolean canTransition(IrExceptionStatus from, Event event) {
    return TRANSITIONS.getOrDefault(from, Map.of()).containsKey(event);
  }

  public static IrExceptionStatus transition(IrExceptionStatus from, Event event) {
    IrExceptionStatus to = TRANSITIONS.getOrDefault(from, Map.of()).get(event);
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

  public static Set<Event> allowedEvents(IrExceptionStatus from) {
    Map<Event, IrExceptionStatus> next = TRANSITIONS.getOrDefault(from, Map.of());
    return next.isEmpty()
        ? Collections.emptySet()
        : Collections.unmodifiableSet(EnumSet.copyOf(next.keySet()));
  }
}
