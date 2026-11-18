package com.bone.procurement.fulfillment.domain.model.invoice;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 发票提交状态与显式迁移表（LLD 05 卷 §5）。
 *
 * <p>关键边界：997 语法回执（finance_ack）只推进到 MATCH_PENDING，绝不置 MATCHED；采购域没有 released 事件入口， BLOCKED→MATCHED
 * 只能由 Finance 事件回写（经 application 层专用方法，不经此通用迁移表）。
 */
public enum InvoiceStatus {
  DRAFT,
  SUBMITTED,
  MATCH_PENDING,
  MATCHED,
  BLOCKED,
  REJECTED;

  public enum Event {
    submit,
    finance_ack,
    invoice_matched,
    exception_raised,
    reject
  }

  private static final Map<InvoiceStatus, Map<Event, InvoiceStatus>> TRANSITIONS;

  static {
    Map<InvoiceStatus, Map<Event, InvoiceStatus>> t = new EnumMap<>(InvoiceStatus.class);
    t.put(DRAFT, Map.of(Event.submit, SUBMITTED, Event.reject, REJECTED));
    // 997 语法回执：仅标记已收到，不等于业务接受
    t.put(SUBMITTED, Map.of(Event.finance_ack, MATCH_PENDING));
    // Finance 匹配结果回传：EXCEPTION → BLOCKED；MATCHED → MATCHED
    t.put(MATCH_PENDING, Map.of());
    // BLOCKED 补料保持 BLOCKED；放行只能由 Finance 事件（application 专用方法），不在通用迁移表暴露 released
    t.put(BLOCKED, Map.of(Event.exception_raised, BLOCKED));
    for (InvoiceStatus terminal : new InvoiceStatus[] {MATCHED, REJECTED}) {
      t.put(terminal, Map.of());
    }
    TRANSITIONS = Collections.unmodifiableMap(t);
  }

  public static boolean canTransition(InvoiceStatus from, Event event) {
    return TRANSITIONS.getOrDefault(from, Map.of()).containsKey(event);
  }

  public static InvoiceStatus transition(InvoiceStatus from, Event event) {
    InvoiceStatus to = TRANSITIONS.getOrDefault(from, Map.of()).get(event);
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

  public static Set<Event> allowedEvents(InvoiceStatus from) {
    Map<Event, InvoiceStatus> next = TRANSITIONS.getOrDefault(from, Map.of());
    return next.isEmpty()
        ? Collections.emptySet()
        : Collections.unmodifiableSet(EnumSet.copyOf(next.keySet()));
  }
}
