package com.bone.claim.infrastructure.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.IdempotentException;
import com.bone.core.idempotency.IdempotencyStore.Snapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * P0-3 幂等存储语义单测（资金安全，2026-11-11 落地）：过期同键 409 拒绝、首快不可覆盖。
 *
 * <p>对应《方案质量诊断报告》§14.1 P0-3 验收判据中的存储层两条；端点层「缺键 400」由 ClaimCaseController / SettlementController 的
 * {@code required=true} 在 Spring 装配层保证。
 */
class IdempotencyStoreAdapterTest {

  private static final long TENANT = 42L;
  private static final String SCOPE_KEY = "42|op-1|key-1|POST|/api/v1/claims";

  private IdempotencyRepository repository;
  private IdempotencyStoreAdapter store;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(IdempotencyRepository.class);
    store = new IdempotencyStoreAdapter(repository);
  }

  @Test
  @DisplayName("P0-3：过期快照不再按未命中放行重新执行，抛 IdempotentException（映射 409）")
  void findExpiredThrowsInsteadOfReExecution() {
    IdempotencyRecord expired =
        IdempotencyRecord.of(42L, SCOPE_KEY, "fp", "{}", Instant.now().minusSeconds(1));
    when(repository.findByCriteria(any())).thenReturn(List.of(expired));

    assertThatThrownBy(() -> store.find(SCOPE_KEY))
        .isInstanceOf(IdempotentException.class)
        .hasMessageContaining("拒绝重新执行");
  }

  @Test
  @DisplayName("P0-3：记录已存在时保留首次快照，不得用后续响应覆盖（不再 save）")
  void putKeepsFirstSnapshotWhenRecordExists() {
    IdempotencyRecord first =
        IdempotencyRecord.of(
            TENANT,
            SCOPE_KEY,
            "fp-first",
            "{\"first\":true}",
            Instant.now().plus(Duration.ofDays(30)));
    when(repository.findByCriteria(any())).thenReturn(List.of(first));

    store.put(SCOPE_KEY, new Snapshot("fp-second", "{\"first\":false}"), Duration.ofDays(30));

    verify(repository, never()).save(any(IdempotencyRecord.class));
    // 首快内容原样可重放
    assertThat(store.find(SCOPE_KEY))
        .hasValueSatisfying(
            snapshot -> {
              assertThat(snapshot.requestFingerprint()).isEqualTo("fp-first");
              assertThat(snapshot.snapshotJson()).isEqualTo("{\"first\":true}");
            });
  }

  @Test
  @DisplayName("并发双写唯一键冲突：保留已存在的首次快照，不再回查覆盖写")
  void putOnUniqueKeyConflictKeepsFirstSnapshot() {
    when(repository.findByCriteria(any())).thenReturn(List.of());
    when(repository.save(any(IdempotencyRecord.class)))
        .thenThrow(new DataIntegrityViolationException("uk_tenant_scope"));

    store.put(SCOPE_KEY, new Snapshot("fp", "{}"), Duration.ofDays(30));

    // 只尝试插入一次；冲突后不再回查 + refresh 覆盖（旧实现会覆盖首快）
    verify(repository, times(1)).save(any(IdempotencyRecord.class));
    verify(repository, times(1)).findByCriteria(any());
  }

  @Test
  @DisplayName("新键正常落库一次")
  void putNewRecordSaved() {
    when(repository.findByCriteria(any())).thenReturn(List.of());
    when(repository.save(any(IdempotencyRecord.class))).thenReturn(1L);

    store.put(SCOPE_KEY, new Snapshot("fp", "{}"), Duration.ofDays(30));

    verify(repository, times(1)).save(any(IdempotencyRecord.class));
  }
}
