package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.evaluation.AiConfidenceCalibration.Verdict;
import com.bone.claim.domain.model.evaluation.BlindLabelTask;
import com.bone.claim.domain.repository.BlindLabelTaskRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 校准报表应用服务：只读消费盲标成对样本；样本不足给 INSUFFICIENT_SAMPLES 不编造数值。 */
class AiCalibrationReportApplicationServiceTest {

  private BlindLabelTaskRepository repository;
  private AiCalibrationReportApplicationService service;

  @BeforeEach
  void setUp() {
    repository = mock(BlindLabelTaskRepository.class);
    service = new AiCalibrationReportApplicationService(repository);
  }

  private static BlindLabelTask paired(double confidence, String aiDecision, String human) {
    BlindLabelTask t =
        BlindLabelTask.open(
            1L,
            42L,
            "BL-1",
            9001L,
            "annotator-1",
            "{\"claimNo\":\"C-1\",\"channel\":\"AUTO\"}",
            "a".repeat(64));
    t.submit("{\"decision\":\"" + human + "\"}", Instant.now());
    t.unblind(
        "{\"aiConfidence\":" + confidence + ",\"decisionResult\":\"" + aiDecision + "\"}",
        BlindLabelTask.Agreement.PAIRED,
        Instant.now());
    return t;
  }

  @Test
  @DisplayName("无样本：NO_EVIDENCE，ECE/Brier 为 null（不是 0）")
  void noEvidence() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of());

    var report = service.report(null, null);

    assertThat(report.fetchedTasks()).isZero();
    assertThat(report.calibration().verdict()).isEqualTo(Verdict.NO_EVIDENCE);
    assertThat(report.calibration().expectedCalibrationError()).isNull();
    assertThat(report.calibration().brierScore()).isNull();
  }

  @Test
  @DisplayName("样本不足：INSUFFICIENT_SAMPLES，观测值保留但禁止重定阈值")
  void insufficientSamples() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of(paired(0.9, "APPROVE", "APPROVE")));

    var report = service.report(null, null);

    assertThat(report.calibration().verdict()).isEqualTo(Verdict.INSUFFICIENT_SAMPLES);
    assertThat(report.calibration().recommendedThreshold()).isNull();
    assertThat(report.calibration().brierScore()).isNotNull();
    assertThat(report.calibration().verdict().permitsRetune()).isFalse();
  }

  @Test
  @DisplayName("minSamples/minSamplesPerSlice 为请求参数缺省（null → 300/30），显式值生效")
  void thresholdsAsRequestParams() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of(paired(0.9, "APPROVE", "APPROVE")));

    assertThat(service.report(null, null).calibration().verdict())
        .isEqualTo(Verdict.INSUFFICIENT_SAMPLES);
    assertThat(service.report(1, 2).calibration().verdict())
        .isEqualTo(Verdict.SLICE_INCOMPLETE); // 总量够但 AUTO 切片 1 条 < perSlice=2
    assertThat(service.report(1, 2).calibration().sampleCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("报告产出后零写操作：仓储只有一次读，绝不改在线配置（AI 不碰钱）")
  void readOnlyAfterReport() {
    when(repository.findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT))
        .thenReturn(List.of());

    service.report(null, null);

    verify(repository).findUnblindedPaired(AiCalibrationReportApplicationService.MAX_LIMIT);
    verifyNoMoreInteractions(repository);
  }
}
