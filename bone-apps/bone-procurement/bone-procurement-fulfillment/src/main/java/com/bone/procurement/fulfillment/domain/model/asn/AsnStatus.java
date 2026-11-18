package com.bone.procurement.fulfillment.domain.model.asn;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** ASN 状态与显式迁移表（LLD 05 卷 §5，未列出即拒绝）。 */
public enum AsnStatus {
  CREATED,
  TRANSMITTED,
  RECEIVING_MATCHED,
  RECONCILED,
  CANCELLED;

  public enum Event {
    transmit,
    goods_received,
    reconciled,
    cancel
  }

  private static final Map<AsnStatus, Map<Event, AsnStatus>> TRANSITIONS;

  static {
    Map<AsnStatus, Map<Event, AsnStatus>> t = new EnumMap<>(AsnStatus.class);
    t.put(CREATED, Map.of(Event.transmit, TRANSMITTED, Event.cancel, CANCELLED));
    t.put(TRANSMITTED, Map.of(Event.goods_received, RECEIVING_MATCHED));
    t.put(RECEIVING_MATCHED, Map.of(Event.reconciled, RECONCILED));
    for (AsnStatus terminal : new AsnStatus[] {RECONCILED, CANCELLED}) {
      t.put(terminal, Map.of());
    }
    TRANSITIONS = Collections.unmodifiableMap(t);
  }

  public static boolean canTransition(AsnStatus from, Event event) {
    return TRANSITIONS.getOrDefault(from, Map.of()).containsKey(event);
  }

  public static AsnStatus transition(AsnStatus from, Event event) {
    AsnStatus to = TRANSITIONS.getOrDefault(from, Map.of()).get(event);
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

  public static Set<Event> allowedEvents(AsnStatus from) {
    Map<Event, AsnStatus> next = TRANSITIONS.getOrDefault(from, Map.of());
    return next.isEmpty()
        ? Collections.emptySet()
        : Collections.unmodifiableSet(EnumSet.copyOf(next.keySet()));
  }
}
