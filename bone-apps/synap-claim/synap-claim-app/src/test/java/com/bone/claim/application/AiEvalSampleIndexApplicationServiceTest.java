package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.AiEvalSampleIndexPort;
import com.bone.claim.domain.model.evaluation.AiEvalSampleCollectedEvent;
import com.bone.claim.domain.model.evaluation.AiEvalSampleCollectedEvent.SampleType;
import com.bone.claim.infrastructure.evaluation.AiEvalSampleIndexPortAdapter;
import com.bone.claim.infrastructure.evaluation.AiEvalSampleProjection;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 运行时索引只读入口的表征钉子：必须真的读到消费方里的数据（防恒空）， 也必须与幂等口径一致（防把重复投递算成多个样本）。 */
class AiEvalSampleIndexApplicationServiceTest {

  private AiEvalSampleProjection projection;
  private AiEvalSampleIndexApplicationService service;

  @BeforeEach
  void setUp() {
    projection = new AiEvalSampleProjection();
    service = new AiEvalSampleIndexApplicationService(new AiEvalSampleIndexPortAdapter(projection));
  }

  @Test
  @DisplayName("空索引如实返回 0 与空列表，不编造占位行")
  void emptyIndexIsReportedHonestly() {
    AiEvalSampleIndexApplicationService.Report report = service.report();
    assertThat(report.indexed()).isZero();
    assertThat(report.samples()).isEmpty();
  }

  @Test
  @DisplayName("消费方有数据时入口必须读到（合规正例，防恒空假实现）")
  void consumedSamplesAreVisibleThroughService() {
    projection.onSampleCollected(
        event("a1b2c3d4e5f60001", "QI-A", "AI_MISJUDGE", SampleType.EVAL_SET));
    projection.onSampleCollected(
        event("a1b2c3d4e5f60002", "QI-B", "QUOTA_ABNORMAL", SampleType.CONTROL_FIX));

    AiEvalSampleIndexApplicationService.Report report = service.report();
    assertThat(report.indexed()).isEqualTo(2);
    assertThat(report.samples())
        .extracting(AiEvalSampleIndexPort.Entry::sampleType)
        .containsExactlyInAnyOrder("EVAL_SET", "CONTROL_FIX");
    assertThat(report.samples()).allSatisfy(e -> assertThat(e.fingerprint()).hasSize(16));
  }

  @Test
  @DisplayName("重复投递幂等后，入口计数不虚增")
  void duplicatedEventDoesNotInflateIndex() {
    projection.onSampleCollected(event("fp-a", "QI-A", "RULE_MISS", SampleType.RULE_FIX));
    projection.onSampleCollected(event("fp-a", "QI-A", "RULE_MISS", SampleType.RULE_FIX));
    assertThat(service.report().indexed()).isEqualTo(1);
  }

  @Test
  @DisplayName("返回快照是不可变副本，调用方无法反向篡改消费方索引")
  void reportSnapshotIsImmutable() {
    projection.onSampleCollected(event("fp-a", "QI-A", "RULE_MISS", SampleType.RULE_FIX));
    assertThat(service.report().samples()).isUnmodifiable();
  }

  private static AiEvalSampleCollectedEvent event(
      String fingerprint, String inspectionNo, String diffType, SampleType sampleType) {
    return new AiEvalSampleCollectedEvent(
        1L,
        42L,
        1001L,
        inspectionNo,
        diffType,
        sampleType,
        "REVIEW",
        fingerprint,
        Instant.parse("2026-11-07T00:00:00Z"));
  }
}
