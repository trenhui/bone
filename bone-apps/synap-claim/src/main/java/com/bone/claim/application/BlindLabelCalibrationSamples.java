package com.bone.claim.application;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.CalibrationSample;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.LabelSource;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 盲标任务 → 校准样本映射（纯函数，无 IO、无状态）。
 *
 * <p>防假绿口径：解析失败计入 {@code unparseableSamples}，绝不编造置信度或结论； sliceKey 取派发载荷 {@code
 * labelPayloadJson.channel}（渠道切片），缺失回退 GLOBAL。 冻结集隔离恒为 true：校准样本只用于观测与阈值建议，冻结集不得参与阈值拟合。
 */
final class BlindLabelCalibrationSamples {

  static final String SLICE_GLOBAL = "GLOBAL";

  private BlindLabelCalibrationSamples() {}

  /** 映射产物：可用的校准样本 + 解析失败计数（失败样本直接丢弃，不补值）。 */
  record Mapping(List<CalibrationSample> samples, int unparseableSamples) {}

  static Mapping from(List<BlindLabelTask> tasks, ObjectMapper mapper) {
    List<CalibrationSample> samples = new ArrayList<>();
    int unparseable = 0;
    for (BlindLabelTask task : tasks) {
      CalibrationSample sample = toSample(task, mapper);
      if (sample == null) {
        unparseable++;
      } else {
        samples.add(sample);
      }
    }
    return new Mapping(List.copyOf(samples), unparseable);
  }

  private static CalibrationSample toSample(BlindLabelTask task, ObjectMapper mapper) {
    JsonNode ai = read(mapper, task.getAiSuggestionJson());
    JsonNode label = read(mapper, task.getLabelResultJson());
    if (ai == null || label == null) {
      return null;
    }
    JsonNode confidenceNode = ai.path("aiConfidence");
    String decisionResult = ai.path("decisionResult").asText("");
    String humanDecision = label.path("decision").asText("");
    if (!confidenceNode.isNumber() || decisionResult.isBlank() || humanDecision.isBlank()) {
      return null;
    }
    BigDecimal confidence = BigDecimal.valueOf(confidenceNode.asDouble());
    if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
      return null;
    }
    String channel = read(mapper, task.getLabelPayloadJson()).path("channel").asText("");
    String sliceKey = channel.isBlank() ? SLICE_GLOBAL : channel;
    return new CalibrationSample(
        confidence,
        decisionResult.equalsIgnoreCase(humanDecision),
        sliceKey,
        LabelSource.BLIND_LABEL,
        true);
  }

  private static JsonNode read(ObjectMapper mapper, String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return mapper.readTree(json);
    } catch (Exception ex) {
      return null;
    }
  }
}
