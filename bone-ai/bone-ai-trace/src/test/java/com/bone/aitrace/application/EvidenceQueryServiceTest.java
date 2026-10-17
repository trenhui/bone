package com.bone.aitrace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.aitrace.domain.model.EvidenceSnapshot;
import com.bone.aitrace.domain.model.EvidenceType;
import com.bone.aitrace.domain.repository.EvidenceSnapshotRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceQueryServiceTest {

  private final EvidenceSnapshotRepository repository = mock(EvidenceSnapshotRepository.class);
  private final EvidenceQueryApplicationService service =
      new EvidenceQueryApplicationService(repository);

  @Test
  void replayByTraceId_delegates_to_repository() {
    when(repository.findByTraceId("t")).thenReturn(List.of());

    assertThat(service.replayByTraceId("t")).isEmpty();
    verify(repository).findByTraceId("t");
  }

  @Test
  void byId_delegates_to_repository() {
    EvidenceSnapshot snapshot =
        EvidenceSnapshot.record(1L, 0L, "t", EvidenceType.LLM_CALL, "h", "m", "p");
    when(repository.findById(1L)).thenReturn(snapshot);

    assertThat(service.byId(1L)).isSameAs(snapshot);
    verify(repository).findById(1L);
  }
}
