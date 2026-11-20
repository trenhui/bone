package com.bone.procurement.contract.domain.model.review;

import java.time.Instant;

/** 追溯链条目：降级 / 停用 / 人工推翻均以此留痕，快照不可变。 */
public record ReviewTraceEntry(
    String reviewId, String eventType, String detail, Instant occurredAt) {

  public static ReviewTraceEntry of(String reviewId, String eventType, String detail) {
    return new ReviewTraceEntry(reviewId, eventType, detail, Instant.now());
  }
}
