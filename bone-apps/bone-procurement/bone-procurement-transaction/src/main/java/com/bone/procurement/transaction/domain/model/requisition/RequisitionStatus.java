package com.bone.procurement.transaction.domain.model.requisition;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** 请购单状态与显式迁移表（未列出的迁移一律拒绝，fail-closed）。 */
public enum RequisitionStatus {
  DRAFT,
  SUBMITTED,
  APPROVED,
  CONVERTED,
  CANCELLED;

  private static final Map<RequisitionStatus, Map<RequisitionEvent, RequisitionStatus>> TRANSITIONS;

  static {
    Map<RequisitionStatus, Map<RequisitionEvent, RequisitionStatus>> t =
        new EnumMap<>(RequisitionStatus.class);
    t.put(DRAFT, Map.of(RequisitionEvent.SUBMIT, SUBMITTED, RequisitionEvent.CANCEL, CANCELLED));
    t.put(
        SUBMITTED,
        Map.of(
            RequisitionEvent.APPROVE, APPROVED,
            RequisitionEvent.REJECT, DRAFT,
            RequisitionEvent.CANCEL, CANCELLED));
    t.put(
        APPROVED, Map.of(RequisitionEvent.CONVERT, CONVERTED, RequisitionEvent.CANCEL, CANCELLED));
    t.put(CONVERTED, Map.of());
    t.put(CANCELLED, Map.of());
    TRANSITIONS = Collections.unmodifiableMap(t);
  }

  public static boolean canTransition(RequisitionStatus from, RequisitionEvent event) {
    return TRANSITIONS.getOrDefault(from, Map.of()).containsKey(event);
  }

  public static RequisitionStatus transition(RequisitionStatus from, RequisitionEvent event) {
    RequisitionStatus to = TRANSITIONS.getOrDefault(from, Map.of()).get(event);
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

  public static Set<RequisitionEvent> allowedEvents(RequisitionStatus from) {
    Map<RequisitionEvent, RequisitionStatus> next = TRANSITIONS.getOrDefault(from, Map.of());
    return next.isEmpty()
        ? Collections.emptySet()
        : Collections.unmodifiableSet(EnumSet.copyOf(next.keySet()));
  }
}
