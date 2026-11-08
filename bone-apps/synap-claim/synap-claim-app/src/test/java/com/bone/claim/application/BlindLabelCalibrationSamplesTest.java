package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.CalibrationSample;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.LabelSource;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 盲标 → 校准样本映射（纯函数）：解析失败计数不编造，channel 切片缺省 GLOBAL。 */
class BlindLabelCalibrationSamplesTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static BlindLabelTask task(String payload, String result, String aiSnapshot) {
    BlindLabelTask t =
        BlindLabelTask.open(1L, 42L, "BL-1", 9001L, "annotator-1", payload, "a".repeat(64));
    t.submit(result, Instant.now());
    t.unblind(aiSnapshot, BlindLabelTask.Agreement.PAIRED, Instant.now());
    return t;
  }

  @Test
  @DisplayName("正常成对样本：confidence/结论/channel切片/冻结集隔离标记齐备")
  void normalSample() {
    BlindLabelTask t =
        task(
            "{\"claimNo\":\"C-1\",\"channel\":\"AUTO\"}",
            "{\"decision\":\"APPROVE\"}",
            "{\"aiConfidence\":0.82,\"decisionResult\":\"APPROVE\"}");

    BlindLabelCalibrationSamples.Mapping m = BlindLabelCalibrationSamples.from(List.of(t), MAPPER);

    assertThat(m.unparseableSamples()).isZero();
    assertThat(m.samples()).hasSize(1);
    CalibrationSample s = m.samples().get(0);
    assertThat(s.confidence().doubleValue()).isEqualTo(0.82);
    assertThat(s.correct()).isTrue();
    assertThat(s.sliceKey()).isEqualTo("AUTO");
    assertThat(s.source()).isEqualTo(LabelSource.BLIND_LABEL);
    assertThat(s.frozenSetExcluded()).isTrue();
  }

  @Test
  @DisplayName("结论不一致 correct=false；无 channel 切片回退 GLOBAL")
  void disagreeAndGlobalSlice() {
    BlindLabelTask t =
        task(
            "{\"claimNo\":\"C-2\"}",
            "{\"decision\":\"REVIEW\"}",
            "{\"aiConfidence\":0.90,\"decisionResult\":\"APPROVE\"}");

    BlindLabelCalibrationSamples.Mapping m = BlindLabelCalibrationSamples.from(List.of(t), MAPPER);

    assertThat(m.samples()).hasSize(1);
    assertThat(m.samples().get(0).correct()).isFalse();
    assertThat(m.samples().get(0).sliceKey()).isEqualTo("GLOBAL");
  }

  @Test
  @DisplayName("解析失败逐条计数：坏 JSON / 缺 aiConfidence / 缺人工结论，绝不编造数值")
  void unparseableCounted() {
    List<BlindLabelTask> tasks =
        List.of(
            task("{\"claimNo\":\"C-3\"}", "{\"decision\":\"APPROVE\"}", "not-json"),
            task(
                "{\"claimNo\":\"C-4\"}",
                "{\"decision\":\"APPROVE\"}",
                "{\"decisionResult\":\"APPROVE\"}"),
            task(
                "{\"claimNo\":\"C-5\"}",
                "{\"other\":1}",
                "{\"aiConfidence\":0.5,\"decisionResult\":\"APPROVE\"}"));

    BlindLabelCalibrationSamples.Mapping m = BlindLabelCalibrationSamples.from(tasks, MAPPER);

    assertThat(m.samples()).isEmpty();
    assertThat(m.unparseableSamples()).isEqualTo(3);
  }

  @Test
  @DisplayName("confidence 越界 [0,1] 计入 unparseable；空列表产出空映射")
  void outOfRangeAndEmpty() {
    BlindLabelTask bad =
        task(
            "{\"claimNo\":\"C-6\"}",
            "{\"decision\":\"APPROVE\"}",
            "{\"aiConfidence\":1.5,\"decisionResult\":\"APPROVE\"}");

    assertThat(BlindLabelCalibrationSamples.from(List.of(bad), MAPPER).unparseableSamples())
        .isEqualTo(1);
    assertThat(BlindLabelCalibrationSamples.from(List.of(), MAPPER).samples()).isEmpty();
  }
}
