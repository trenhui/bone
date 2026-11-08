package com.bone.claim.infrastructure.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.application.port.out.AiEvalSampleIndexPort;
import com.bone.claim.domain.model.evaluation.AiEvalSampleCollectedEvent;
import com.bone.claim.domain.model.evaluation.AiEvalSampleCollectedEvent.SampleType;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 事件真实消费方的表征钉子：幂等、有界、null 安全、归因可观测。
 *
 * <p>与 {@code DiffSampleExportApplicationServiceTest} 分工：那边守「落库事实源」， 这边守「事件不再零消费方」。两者都不宣称完成 Phase B
 * 的评测集/盲标闭环。
 */
class AiEvalSampleProjectionTest {

  private AiEvalSampleProjection projection;

  @BeforeEach
  void setUp() {
    projection = new AiEvalSampleProjection();
  }

  @Test
  @DisplayName("事件被真实消费并可按归因观测（不再是零消费方）")
  void sampleIsConsumedAndObservable() {
    projection.onSampleCollected(
        sample("fp-1", 9001L, "INSP-1", "AI_MISJUDGE", SampleType.EVAL_SET));
    assertThat(projection.size()).isEqualTo(1);
    AiEvalSampleIndexPort.Entry entry = projection.snapshot().get(0);
    assertThat(entry.sampleType()).isEqualTo("EVAL_SET");
    assertThat(entry.claimId()).isEqualTo(9001L);
    assertThat(entry.inspectionNo()).isEqualTo("INSP-1");
  }

  @Test
  @DisplayName("同 fingerprint 重复投递幂等去重，不同 fingerprint 不串样")
  void duplicateFingerprintIsIdempotent() {
    projection.onSampleCollected(sample("fp-1", 9001L, "INSP-1", "RULE_MISS", SampleType.RULE_FIX));
    projection.onSampleCollected(sample("fp-1", 9001L, "INSP-1", "RULE_MISS", SampleType.RULE_FIX));
    projection.onSampleCollected(
        sample("fp-2", 9002L, "INSP-2", "QUOTA_ABNORMAL", SampleType.CONTROL_FIX));
    assertThat(projection.size()).isEqualTo(2);
    assertThat(projection.snapshot())
        .extracting(AiEvalSampleIndexPort.Entry::sampleType)
        .containsExactlyInAnyOrder("RULE_FIX", "CONTROL_FIX");
  }

  @Test
  @DisplayName("null 事件 / null 指纹不得抛异常打断 AFTER_COMMIT 回调")
  void nullInputsAreIgnored() {
    projection.onSampleCollected(null);
    projection.onSampleCollected(sample(null, 9001L, "INSP-1", "RULE_MISS", SampleType.RULE_FIX));
    assertThat(projection.size()).isZero();
  }

  @Test
  @DisplayName("超过上界时按发生时间淘汰最旧，索引保持有界（防无界内存）")
  void oversizedIndexIsBounded() {
    for (int i = 0; i < 520; i++) {
      projection.onSampleCollected(
          sample("fp-" + i, 9000L + i, "INSP-" + i, "RULE_MISS", SampleType.RULE_FIX));
    }
    assertThat(projection.size()).isLessThanOrEqualTo(500);
    // 最新的必须还在（淘汰的是最旧）
    assertThat(projection.snapshot())
        .extracting(AiEvalSampleIndexPort.Entry::fingerprint)
        .contains("fp-519");
  }

  @Test
  @DisplayName("快照是只读副本，外部无法篡改内部索引")
  void snapshotIsImmutableCopy() {
    projection.onSampleCollected(sample("fp-1", 9001L, "INSP-1", "RULE_MISS", SampleType.RULE_FIX));
    assertThat(projection.snapshot()).isUnmodifiable();
  }

  private static AiEvalSampleCollectedEvent sample(
      String fingerprint,
      Long claimId,
      String inspectionNo,
      String diffType,
      SampleType sampleType) {
    return new AiEvalSampleCollectedEvent(
        1L,
        42L,
        claimId,
        inspectionNo,
        diffType,
        sampleType,
        "RULE_REVIEW",
        fingerprint,
        Instant.parse("2026-11-07T00:00:00Z"));
  }
}
