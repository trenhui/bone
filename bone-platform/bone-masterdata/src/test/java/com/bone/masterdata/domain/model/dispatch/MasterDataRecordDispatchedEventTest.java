package com.bone.masterdata.domain.model.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 分发事件契约纯单测：幂等键、版本单调、以及「不回传 PII 原文」的结构性约束。 */
class MasterDataRecordDispatchedEventTest {

  private static final Instant T = Instant.parse("2026-11-13T00:00:00Z");

  private static MasterDataRecordDispatchedEvent event(
      String id, long tenant, String party, long version, String type, String hash) {
    return MasterDataRecordDispatchedEvent.of(id, tenant, party, version, type, 1, T, hash);
  }

  @Test
  void idempotencyKeyIsStableForSameEnvelope() {
    assertEquals(
        event("E-1", 7L, "P-1", 3, "RECORD_PUBLISHED", "h1").idempotencyKey(),
        event("E-2", 7L, "P-1", 3, "RECORD_PUBLISHED", "h1").idempotencyKey());
  }

  @Test
  void differentVersionOrEventTypeYieldsDifferentKey() {
    String base = event("E-1", 7L, "P-1", 3, "RECORD_PUBLISHED", "h1").idempotencyKey();
    assertNotEquals(base, event("E-2", 7L, "P-1", 4, "RECORD_PUBLISHED", "h1").idempotencyKey());
    assertNotEquals(base, event("E-3", 7L, "P-1", 3, "RECORD_MERGED", "h1").idempotencyKey());
  }

  @Test
  void versionIsMonotonicWithinSameTenantAndParty() {
    assertTrue(
        event("E-2", 7L, "P-1", 4, "T", "h").isNewerThan(event("E-1", 7L, "P-1", 3, "T", "h")));
    assertFalse(
        event("E-1", 7L, "P-1", 3, "T", "h").isNewerThan(event("E-2", 7L, "P-1", 4, "T", "h")));
  }

  @Test
  void comparingAcrossTenantsOrPartiesFailsClosed() {
    MasterDataRecordDispatchedEvent base = event("E-1", 7L, "P-1", 3, "T", "h");
    assertThrows(
        DomainException.class, () -> base.isNewerThan(event("E-2", 8L, "P-1", 2, "T", "h")));
    assertThrows(
        DomainException.class, () -> base.isNewerThan(event("E-3", 7L, "P-2", 2, "T", "h")));
  }

  @Test
  void missingEnvelopeFieldsAreRejected() {
    assertThrows(DomainException.class, () -> event("E-1", 7L, "P-1", 3, "T", "  "));
    assertThrows(DomainException.class, () -> event("E-1", 7L, "P-1", 0, "T", "h"));
  }

  @Test
  void eventEnvelopeNeverCarriesPayloadPlaintext() {
    Field[] fields = MasterDataRecordDispatchedEvent.class.getDeclaredFields();
    assertTrue(
        Arrays.stream(fields).noneMatch(f -> f.getName().equals("payload")),
        "事件信封不得携带 payload 原文（PII 最小化）");
    assertTrue(
        Arrays.stream(MasterDataRecordDispatchedEvent.class.getDeclaredMethods())
            .noneMatch(m -> m.getName().equals("getPayload")),
        "不得暴露 payload 原文读取入口");
  }
}
