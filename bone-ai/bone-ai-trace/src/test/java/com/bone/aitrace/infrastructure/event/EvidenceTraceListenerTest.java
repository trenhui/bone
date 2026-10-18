package com.bone.aitrace.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.event.LlmCallRecorded;
import com.bone.aitrace.domain.gateway.TenantProvider;
import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.model.EvidenceType;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceTraceListenerTest {

  private final EvidenceSnapshotRepository repository = mock(EvidenceSnapshotRepository.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);
  private final EvidenceTraceListener listener =
      new EvidenceTraceListener(repository, tenantProvider);

  private final LlmCallRecorded event =
      LlmCallRecorded.of(
          LlmRequest.of("gpt-4o-mini", List.of(LlmMessage.system("s"), LlmMessage.user("hi"))),
          new LlmResponse("ans", "gpt-4o-mini", "stop", new LlmUsage(1, 2, 3)));

  @Test
  void toSnapshot_builds_triple_with_tenant_and_hash() {
    EvidenceSnapshot snapshot = EvidenceTraceListener.toSnapshot(event, 42L);

    assertThat(snapshot.getTenantId()).isEqualTo(42L);
    assertThat(snapshot.getTraceId()).isEqualTo(event.traceId());
    assertThat(snapshot.type()).isEqualTo(EvidenceType.LLM_CALL);
    assertThat(snapshot.getEngineVersion()).isEqualTo("gpt-4o-mini");
    assertThat(snapshot.getInputHash()).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(snapshot.getPayload()).contains(event.traceId()).contains("ans").contains("hi");
  }

  @Test
  void onLlmCallRecorded_inserts_snapshot_with_current_tenant() {
    when(tenantProvider.currentTenantId()).thenReturn(7L);
    // 无历史快照 → 非重复，正常落库
    when(repository.findByTraceId(event.traceId())).thenReturn(List.of());

    listener.onLlmCallRecorded(event);

    verify(repository)
        .insert(
            argThat(
                s ->
                    s.getTenantId().equals(7L)
                        && event.traceId().equals(s.getTraceId())
                        && s.type() == EvidenceType.LLM_CALL));
  }

  @Test
  void duplicate_event_does_not_insert_twice() {
    when(tenantProvider.currentTenantId()).thenReturn(7L);
    EvidenceSnapshot existing = EvidenceTraceListener.toSnapshot(event, 7L);
    // 同 traceId + 同输入哈希 → 同一事件重放
    when(repository.findByTraceId(event.traceId())).thenReturn(List.of(existing));

    listener.onLlmCallRecorded(event);

    verify(repository, never()).insert(any());
  }

  @Test
  void same_trace_id_but_different_input_still_inserts() {
    when(tenantProvider.currentTenantId()).thenReturn(7L);
    // 一次业务动作多轮编排共用 traceId：不同输入的调用必须各自留证，不能被幂等误吞
    EvidenceSnapshot otherTurn =
        EvidenceTraceListener.toSnapshot(
            LlmCallRecorded.of(
                LlmRequest.of("gpt-4o-mini", List.of(LlmMessage.user("different turn"))),
                new LlmResponse("ans2", "gpt-4o-mini", "stop", new LlmUsage(1, 2, 3))),
            7L);
    when(repository.findByTraceId(event.traceId())).thenReturn(List.of(otherTurn));

    listener.onLlmCallRecorded(event);

    verify(repository).insert(any());
  }
}
