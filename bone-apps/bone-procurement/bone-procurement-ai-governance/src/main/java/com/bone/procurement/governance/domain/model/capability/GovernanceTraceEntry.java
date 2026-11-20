package com.bone.procurement.governance.domain.model.capability;

import java.time.Instant;
import java.util.Objects;

/** 治理追溯链条目：登记 / 启用 / 停用 / 降级 / 不可判定 / 责任人变更 / 人工终决 / override 均留痕。 */
public record GovernanceTraceEntry(
    String capabilityCode, String eventType, String detail, Instant occurredAt) {

  public GovernanceTraceEntry {
    Objects.requireNonNull(capabilityCode, "capabilityCode");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (detail == null) {
      detail = "";
    }
  }

  public static GovernanceTraceEntry of(String capabilityCode, String eventType, String detail) {
    return new GovernanceTraceEntry(capabilityCode, eventType, detail, Instant.now());
  }
}
