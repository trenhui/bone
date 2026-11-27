package com.bone.claim.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.MrmGovernanceApplicationService;
import com.bone.claim.application.MrmGovernanceApplicationService.BiasMonitoringReport;
import com.bone.claim.application.MrmGovernanceApplicationService.GovernanceReport;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MRM 治理端点守卫：对外下限不可被查询参数下调，端点确实按守卫后的值调用服务。 */
class MrmGovernanceControllerGuardTest {

  @Test
  @DisplayName("minSamplesPerSlice 低于/等于治理下限时钳到 30，高于则保留，超大封顶 500")
  void guardedMinSamples() {
    assertThat(MrmGovernanceController.guardedMinSamples(null)).isEqualTo(30);
    assertThat(MrmGovernanceController.guardedMinSamples(1)).isEqualTo(30);
    assertThat(MrmGovernanceController.guardedMinSamples(100)).isEqualTo(100);
    assertThat(MrmGovernanceController.guardedMinSamples(Integer.MAX_VALUE)).isEqualTo(500);
  }

  @Test
  @DisplayName("端点按守卫值调用服务（防参数绕过，非仅函数级）")
  void controllerPassesGuardedValue() {
    MrmGovernanceApplicationService service = mock(MrmGovernanceApplicationService.class);
    when(service.governance(LocalDate.of(2026, 11, 27))).thenReturn(mock(GovernanceReport.class));
    when(service.biasMonitoring("CHANNEL", 30)).thenReturn(mock(BiasMonitoringReport.class));
    MrmGovernanceController controller = new MrmGovernanceController(service);

    controller.governance(LocalDate.of(2026, 11, 27));
    controller.biasMonitoring("CHANNEL", 1);

    verify(service).governance(LocalDate.of(2026, 11, 27));
    verify(service).biasMonitoring("CHANNEL", 30);
  }
}
