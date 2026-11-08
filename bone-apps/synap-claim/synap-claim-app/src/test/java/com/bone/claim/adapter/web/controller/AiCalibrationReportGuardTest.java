package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.AiCalibrationReportApplicationService;
import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration;
import com.bone.claim.domain.repository.BlindLabelTaskRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 校准报表只读端点的治理下限（业界实践：Google《ML Test Score》要求把每条测试写成可执行 assertion；Sculley《Hidden Technical
 * Debt》把「未版本化的阈值配置」列为失败模式）。
 *
 * <p>HTTP 查询参数只允许上调最小样本量，不得下调到缺省治理下限以下：否则一条盲标样本 即可产出 THRESHOLD_PROPOSED 阈值建议，绕过「样本不足禁止重定阈值」的防假绿闸。
 * 领域内核仍允许任意 minSamples 供离线实验，守卫只加在对外入口。
 */
class AiCalibrationReportGuardTest {

  private static final int FLOOR = AiCalibrationReportApplicationService.DEFAULT_MIN_SAMPLES;
  private static final int SLICE_FLOOR =
      AiCalibrationReportApplicationService.DEFAULT_MIN_SAMPLES_PER_SLICE;

  @Test
  @DisplayName("null 回退到治理下限")
  void nullFallsBackToFloor() {
    assertThat(AiCalibrationReportController.guardedMinSamples(null, FLOOR)).isEqualTo(FLOOR);
  }

  @Test
  @DisplayName("低于下限（含 0 / 负数 / 299）一律钳制到下限")
  void belowFloorIsClamped() {
    for (int v : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 299}) {
      assertThat(AiCalibrationReportController.guardedMinSamples(v, FLOOR))
          .as("requested=%s", v)
          .isEqualTo(FLOOR);
    }
  }

  @Test
  @DisplayName("高于下限允许上调（离线更严格口径）")
  void aboveFloorIsPreserved() {
    assertThat(AiCalibrationReportController.guardedMinSamples(300, FLOOR)).isEqualTo(300);
    assertThat(AiCalibrationReportController.guardedMinSamples(5000, FLOOR)).isEqualTo(5000);
  }

  @Test
  @DisplayName("切片下限独立生效，不与全局下限混用")
  void sliceFloorIndependent() {
    assertThat(AiCalibrationReportController.guardedMinSamples(1, SLICE_FLOOR))
        .isEqualTo(SLICE_FLOOR);
    assertThat(SLICE_FLOOR).isNotEqualTo(FLOOR);
  }

  @Test
  @DisplayName("端点真实按下限调用服务（防参数绕过，非仅函数级）")
  void controllerPassesFloorsToService() {
    AiCalibrationReportApplicationService service =
        mock(AiCalibrationReportApplicationService.class);
    AiCalibrationReportController controller = new AiCalibrationReportController(service);

    controller.report(1, 1);

    verify(service).report(FLOOR, SLICE_FLOOR);
  }

  @Test
  @DisplayName("端点 null 参数同样按下限调用服务")
  void controllerNullParamsUseFloors() {
    AiCalibrationReportApplicationService service =
        mock(AiCalibrationReportApplicationService.class);
    AiCalibrationReportController controller = new AiCalibrationReportController(service);

    controller.report(null, null);

    verify(service).report(FLOOR, SLICE_FLOOR);
  }

  @Test
  @DisplayName("业务后果：1 条样本在治理下限下绝不产出阈值建议")
  void oneSampleCannotProposeThreshold() {
    AiConfidenceCalibration.CalibrationSample sample =
        new AiConfidenceCalibration.CalibrationSample(
            new BigDecimal("0.90"),
            true,
            "AUTO",
            AiConfidenceCalibration.LabelSource.BLIND_LABEL,
            true);

    AiConfidenceCalibration.CalibrationReport report =
        AiConfidenceCalibration.evaluate(
            List.of(sample),
            new BigDecimal("0.70"),
            new BigDecimal("0.05"),
            AiCalibrationReportController.guardedMinSamples(1, FLOOR),
            AiCalibrationReportController.guardedMinSamples(1, SLICE_FLOOR));

    assertThat(report.verdict().permitsRetune()).isFalse();
    assertThat(report.recommendedThreshold()).isNull();
    assertThat(report.verdict()).isEqualTo(AiConfidenceCalibration.Verdict.INSUFFICIENT_SAMPLES);
  }

  @Test
  @DisplayName("回传实际生效下限（可观测，供审计与 E2E 验证）")
  void serviceReportsEffectiveFloors() {
    BlindLabelTaskRepository repository = mock(BlindLabelTaskRepository.class);
    when(repository.findUnblindedPaired(anyInt())).thenReturn(List.of());
    AiCalibrationReportApplicationService service =
        new AiCalibrationReportApplicationService(repository);

    assertThat(service.report(1, 2).effectiveMinSamples()).isEqualTo(1);
    assertThat(service.report(1, 2).effectiveMinSamplesPerSlice()).isEqualTo(2);
  }
}
