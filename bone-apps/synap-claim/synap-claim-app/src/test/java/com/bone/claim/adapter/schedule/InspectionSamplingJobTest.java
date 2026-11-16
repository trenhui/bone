package com.bone.claim.adapter.schedule;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.FeatureTenantToggleService;
import com.bone.claim.application.InspectionApplicationService;
import com.bone.claim.application.InspectionApplicationService.SampleOutcome;
import com.bone.claim.domain.model.claim.projection.ClaimTenantRef;
import com.bone.claim.domain.model.inspection.InspectionConfig;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.infrastructure.schedule.ScheduleLockService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 后督抽样 Job 测试：全租户扫描 + 逐笔显式携带租户 + 单笔异常续跑 + 比例透传。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionSamplingJobTest {

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private InspectionApplicationService inspectionApplicationService;
  @Mock private ScheduleLockService scheduleLockService;
  @Mock private FeatureTenantToggleService featureTenantToggleService;

  private InspectionSamplingJob job;

  @BeforeEach
  void setUp() {
    job =
        new InspectionSamplingJob(
            claimCaseRepository,
            inspectionApplicationService,
            scheduleLockService,
            featureTenantToggleService);
    ReflectionTestUtils.setField(job, "batch", 100);
    // 比例来自 t_feature_toggle（批4 4.1）；不同租户可配不同比例，测试钉住"按租户读"而非写死。
    when(featureTenantToggleService.inspectionConfig(anyLong()))
        .thenReturn(new InspectionConfig(5, java.util.Map.of()));
    when(inspectionApplicationService.sampleIfEligible(anyLong(), anyInt()))
        .thenReturn(SampleOutcome.NOT_SAMPLED);
  }

  @Test
  @DisplayName("逐笔抽样并透传配置比例（比例写死会被此断言拦住）")
  void samplesEachCandidateWithConfiguredRate() {
    when(claimCaseRepository.findFinishedForInspectionAllTenants(100))
        .thenReturn(List.of(ref(1L, 7L), ref(2L, 8L)));

    job.sampleDailyBody();

    verify(inspectionApplicationService).sampleIfEligible(1L, 5);
    verify(inspectionApplicationService).sampleIfEligible(2L, 5);
  }

  @Test
  @DisplayName("单笔异常不中断批次（否则一个脏案件堵住整个租户的抽样）")
  void continuesWhenOneCandidateFails() {
    when(claimCaseRepository.findFinishedForInspectionAllTenants(100))
        .thenReturn(List.of(ref(1L, 7L), ref(2L, 8L)));
    doThrow(new RuntimeException("boom"))
        .when(inspectionApplicationService)
        .sampleIfEligible(eq(1L), anyInt());

    job.sampleDailyBody();

    verify(inspectionApplicationService, times(1)).sampleIfEligible(eq(1L), anyInt());
    verify(inspectionApplicationService, times(1)).sampleIfEligible(eq(2L), anyInt());
  }

  @Test
  @DisplayName("比例按租户配置读取并透传（批4 4.1：配置服务是真源，非@Value写死）")
  void usesPerTenantConfiguredRate() {
    when(claimCaseRepository.findFinishedForInspectionAllTenants(100))
        .thenReturn(List.of(ref(1L, 7L), ref(2L, 8L)));
    when(featureTenantToggleService.inspectionConfig(7L))
        .thenReturn(new InspectionConfig(30, java.util.Map.of()));
    when(featureTenantToggleService.inspectionConfig(8L))
        .thenReturn(new InspectionConfig(80, java.util.Map.of()));

    job.sampleDailyBody();

    verify(inspectionApplicationService).sampleIfEligible(1L, 30);
    verify(inspectionApplicationService).sampleIfEligible(2L, 80);
    verify(featureTenantToggleService).inspectionConfig(7L);
    verify(featureTenantToggleService).inspectionConfig(8L);
  }

  private static ClaimTenantRef ref(long claimId, long tenantId) {
    return new ClaimTenantRef(tenantId, claimId, "CL" + claimId, "FINISHED");
  }
}
