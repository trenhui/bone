package com.bone.aidoc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.aidoc.domain.service.ThresholdCalibrator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 标定报告生产就绪闸门：防止「未达标阈值被当成生产配置」的假置信。 */
class CalibrationReportProductionReadyTest {

  private static CalibrationSample s(RiskLevel level, double conf, double q, boolean correct) {
    return CalibrationSample.of(level, conf, q, correct);
  }

  @Test
  void empty_report_is_not_production_ready() {
    CalibrationReport r = CalibrationReport.empty(0.0001);
    assertThat(r.allLevelsProven()).isFalse();
    assertThat(r.productionReady()).isFalse();
    assertThat(r.productionReady(100)).isFalse();
  }

  @Test
  void single_level_report_is_not_production_ready_even_if_proven() {
    // 只标定 HIGH 一个等级且全对——此前 allLevelsProven 会误判全级别达标
    List<CalibrationSample> samples = new ArrayList<>();
    for (int i = 0; i < ThresholdCalibrator.minimumSampleSizeForZeroErrors(0.0001) + 10; i++) {
      samples.add(s(RiskLevel.HIGH, 0.999, 0.99, true));
    }
    CalibrationReport r = new ThresholdCalibrator(0.0001).calibrate(samples);
    assertThat(r.allLevelsProven()).isFalse();
    assertThat(r.productionReady()).isFalse();
  }

  @Test
  void all_levels_proven_but_under_sampled_is_not_production_ready() {
    // 四等级齐全但每等级只喂 5 个样本（远小于 Wilson 最小样本）→ 不应 proven
    List<CalibrationSample> samples = new ArrayList<>();
    for (RiskLevel level : RiskLevel.values()) {
      for (int i = 0; i < 5; i++) {
        samples.add(s(level, 0.999, 0.99, true));
      }
    }
    CalibrationReport r = new ThresholdCalibrator(0.0001).calibrate(samples);
    assertThat(r.allLevelsProven()).isFalse();
    assertThat(r.productionReady()).isFalse();
  }

  @Test
  void sufficiently_sampled_all_levels_becomes_production_ready() {
    List<CalibrationSample> samples = new ArrayList<>();
    int need = ThresholdCalibrator.minimumSampleSizeForZeroErrors(0.0001) + 50;
    for (RiskLevel level : RiskLevel.values()) {
      for (int i = 0; i < need; i++) {
        samples.add(s(level, 0.999, 0.99, true));
      }
    }
    CalibrationReport r = new ThresholdCalibrator(0.0001).calibrate(samples);
    assertThat(r.allLevelsProven()).isTrue();
    assertThat(r.productionReady()).isTrue();
    assertThat(r.warnings()).noneMatch(w -> w.contains("未达生产就绪"));
  }

  @Test
  void calibrator_warns_when_not_production_ready() {
    List<CalibrationSample> samples = new ArrayList<>();
    for (RiskLevel level : RiskLevel.values()) {
      for (int i = 0; i < 5; i++) {
        samples.add(s(level, 0.999, 0.99, true));
      }
    }
    CalibrationReport r = new ThresholdCalibrator(0.0001).calibrate(samples);
    assertThat(r.warnings()).anyMatch(w -> w.contains("未达生产就绪"));
  }
}
