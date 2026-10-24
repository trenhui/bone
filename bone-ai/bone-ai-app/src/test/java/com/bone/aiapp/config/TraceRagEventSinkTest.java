package com.bone.aiapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.airag.domain.model.RagEvent;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.model.EvidenceType;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TraceRagEventSink} 单测（R6）：锁死「落 RAG_EVENT 快照 / 租户缺失跳过不抛 / 落库故障不影响检索」三条契约。 */
class TraceRagEventSinkTest {

  private final EvidenceSnapshotRepository repository = mock(EvidenceSnapshotRepository.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);
  private final TraceRagEventSink sink = new TraceRagEventSink(repository, tenantProvider);

  private static final RagEvent EVENT =
      new RagEvent("DEGRADE_EMBEDDING", "tenant=1, fallback=BM25");

  @Test
  @DisplayName("事件落为 RAG_EVENT 证据快照（带当前租户 / 64 位输入哈希 / 载荷含事件内容）")
  void records_event_as_rag_event_snapshot() {
    when(tenantProvider.currentTenantId()).thenReturn(7L);

    sink.record(EVENT);

    verify(repository)
        .insert(
            argThat(
                s ->
                    s.getTenantId().equals(7L)
                        && s.type() == EvidenceType.RAG_EVENT
                        && s.getTraceId() != null
                        && s.getInputHash().matches("[0-9a-f]{64}")
                        && s.getPayload().contains("DEGRADE_EMBEDDING")
                        && s.getPayload().contains("fallback=BM25")));
  }

  @Test
  @DisplayName("租户缺失：WARN 后跳过落库，不得抛异常打断检索（读路径可用性优先）")
  void tenant_missing_skips_without_throwing() {
    when(tenantProvider.currentTenantId()).thenReturn(null);

    assertThatNoException().isThrownBy(() -> sink.record(EVENT));
    verify(repository, never()).insert(any());
  }

  @Test
  @DisplayName("落库故障：降级为 WARN，不向检索链抛出（审计设施抖动不得影响检索）")
  void repository_failure_does_not_propagate() {
    when(tenantProvider.currentTenantId()).thenReturn(7L);
    doThrow(new RuntimeException("db down")).when(repository).insert(any(EvidenceSnapshot.class));

    assertThatNoException().isThrownBy(() -> sink.record(EVENT));
  }

  @Test
  @DisplayName("取租户本身故障：同样不抛出")
  void tenant_provider_failure_does_not_propagate() {
    when(tenantProvider.currentTenantId()).thenThrow(new IllegalStateException("ctx broken"));

    assertThatNoException().isThrownBy(() -> sink.record(EVENT));
    verify(repository, never()).insert(any());
  }

  @Test
  @DisplayName("toSnapshot 构造三元组：RAG_EVENT 类型 / 固定引擎版本 / 载荷含事件类型与详情")
  void toSnapshot_builds_triple() {
    EvidenceSnapshot snapshot = TraceRagEventSink.toSnapshot(EVENT, 5L);

    assertThat(snapshot.getTenantId()).isEqualTo(5L);
    assertThat(snapshot.type()).isEqualTo(EvidenceType.RAG_EVENT);
    assertThat(snapshot.getEngineVersion()).isEqualTo(TraceRagEventSink.ENGINE_VERSION);
    assertThat(snapshot.getInputHash()).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(snapshot.getPayload()).contains("eventType").contains("DEGRADE_EMBEDDING");
  }

  @Test
  @DisplayName("空事件：静默忽略（不落库、不抛）")
  void null_event_is_ignored() {
    assertThatNoException().isThrownBy(() -> sink.record(null));
    verify(repository, never()).insert(any());
  }
}
