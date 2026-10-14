package com.bone.aitrace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvidenceSnapshotTest {

  @Test
  void record_sets_fields_and_type_roundtrip() {
    EvidenceSnapshot snapshot =
        EvidenceSnapshot.record(
            1L, 9L, "trace-1", EvidenceType.LLM_CALL, "hash", "gpt-4o-mini", "{}");

    assertThat(snapshot.getId()).isEqualTo(1L);
    assertThat(snapshot.getTenantId()).isEqualTo(9L);
    assertThat(snapshot.getTraceId()).isEqualTo("trace-1");
    assertThat(snapshot.getInputHash()).isEqualTo("hash");
    assertThat(snapshot.getEngineVersion()).isEqualTo("gpt-4o-mini");
    assertThat(snapshot.getPayload()).isEqualTo("{}");
    assertThat(snapshot.getDeleted()).isFalse();
    assertThat(snapshot.getCreatedAt()).isNotNull();
    assertThat(snapshot.getUpdatedAt()).isNotNull();
    assertThat(snapshot.type()).isEqualTo(EvidenceType.LLM_CALL);
  }
}
