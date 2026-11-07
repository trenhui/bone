package com.bone.aidoc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.gateway.OcrMetrics;
import com.bone.aidoc.domain.gateway.RecordingOcrMetrics;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.ReviewStatus;
import org.junit.jupiter.api.Test;

/** OcrMetrics 端口契约：no-op 不采集，recording 如实计数。 */
class OcrMetricsTest {

  @Test
  void noop_emits_zero_snapshot_and_ignores_calls() {
    OcrMetrics m = new NoOpOcrMetrics();
    m.countDecision(ReviewStatus.AUTO_PASSED);
    m.countCriticalAutoPass();
    m.countL4Mismatch(3);
    m.countSafetyGate(2);
    m.countClassification(OcrDocType.INVOICE);
    assertThat(m.snapshot()).isEqualTo(OcrMetrics.Snapshot.zero());
  }

  @Test
  void recording_accumulates_counts() {
    RecordingOcrMetrics m = new RecordingOcrMetrics();
    m.countDecision(ReviewStatus.AUTO_PASSED);
    m.countDecision(ReviewStatus.AUTO_PASSED);
    m.countDecision(ReviewStatus.MANUAL_REVIEW);
    m.countCriticalAutoPass();
    m.countCriticalAutoPass();
    m.countL4Mismatch(4);
    m.countSafetyGate(1);
    m.countClassification(OcrDocType.INVOICE);

    assertThat(m.decisionsOf(ReviewStatus.AUTO_PASSED)).isEqualTo(2);
    assertThat(m.decisionsOf(ReviewStatus.MANUAL_REVIEW)).isEqualTo(1);
    assertThat(m.snapshot().criticalAutoPassed()).isEqualTo(2);
    assertThat(m.snapshot().l4Mismatch()).isEqualTo(4);
    assertThat(m.snapshot().safetyGates()).isEqualTo(1);
    assertThat(m.classifications()).isEqualTo(1);
  }
}
