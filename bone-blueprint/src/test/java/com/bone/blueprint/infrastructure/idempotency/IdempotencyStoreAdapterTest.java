package com.bone.blueprint.infrastructure.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.IdempotencyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 幂等快照落库适配器测试（API 规范 §8：TTL 24h、过期即视为未命中）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyStoreAdapterTest {

  private static final String SCOPE_KEY = "7|9|key-1|POST|/api/v1/orders";

  @Mock private IdempotencyRepository repository;

  @InjectMocks private IdempotencyStoreAdapter adapter;

  @Test
  void findReturnsEmptyWhenNoRecord() {
    when(repository.findByCriteria(any())).thenReturn(List.of());

    assertTrue(adapter.find(SCOPE_KEY).isEmpty());
  }

  @Test
  void findReturnsSnapshotWhenNotExpired() {
    when(repository.findByCriteria(any()))
        .thenReturn(List.of(record(Instant.now().plus(Duration.ofHours(1)))));

    IdempotencyStore.Snapshot snapshot = adapter.find(SCOPE_KEY).orElseThrow();

    assertEquals("fp-1", snapshot.requestFingerprint());
    assertEquals("{\"status\":201}", snapshot.snapshotJson());
  }

  @Test
  void findTreatsExpiredRecordAsMiss() {
    when(repository.findByCriteria(any()))
        .thenReturn(List.of(record(Instant.now().minus(Duration.ofHours(1)))));

    // 过期后同一 Idempotency-Key 可重新使用（TTL 24h）
    assertTrue(adapter.find(SCOPE_KEY).isEmpty());
    verify(repository, never()).update(any());
  }

  @Test
  void putInsertsNewRecordWithTenantFromScopeKey() {
    when(repository.findByCriteria(any())).thenReturn(List.of());

    adapter.put(
        SCOPE_KEY, new IdempotencyStore.Snapshot("fp-2", "{\"status\":201}"), Duration.ofHours(24));

    ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
    verify(repository).insert(captor.capture());
    IdempotencyRecord saved = captor.getValue();
    assertEquals(7L, saved.getTenantId());
    assertEquals("fp-2", saved.getRequestFingerprint());
    assertFalse(saved.expiredAt(Instant.now()));
  }

  @Test
  void putRefreshesExistingRecord() {
    IdempotencyRecord existing = record(Instant.now().plus(Duration.ofHours(1)));
    when(repository.findByCriteria(any())).thenReturn(List.of(existing));

    adapter.put(
        SCOPE_KEY, new IdempotencyStore.Snapshot("fp-3", "{\"status\":200}"), Duration.ofHours(24));

    assertEquals("fp-3", existing.getRequestFingerprint());
    assertTrue(existing.getExpiresAt().isAfter(Instant.now().plus(Duration.ofHours(23))));
    verify(repository).update(existing);
    verify(repository, never()).insert(any());
  }

  private static IdempotencyRecord record(Instant expiresAt) {
    return IdempotencyRecord.of(7L, SCOPE_KEY, "fp-1", "{\"status\":201}", expiresAt);
  }
}
