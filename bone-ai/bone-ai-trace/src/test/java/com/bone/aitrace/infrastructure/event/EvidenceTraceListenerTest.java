package com.bone.aitrace.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
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

    listener.onLlmCallRecorded(event);

    verify(repository)
        .insert(
            argThat(
                s ->
                    s.getTenantId().equals(7L)
                        && event.traceId().equals(s.getTraceId())
                        && s.type() == EvidenceType.LLM_CALL));
  }
}
