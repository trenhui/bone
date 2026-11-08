package com.bone.claim.adapter.schedule;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.InspectionApplicationService;
import com.bone.claim.application.InspectionApplicationService.SampleOutcome;
import com.bone.claim.domain.model.claim.projection.ClaimTenantRef;
import com.bone.claim.domain.repository.ClaimCaseRepository;
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

  private InspectionSamplingJob job;

  @BeforeEach
  void setUp() {
    job = new InspectionSamplingJob(claimCaseRepository, inspectionApplicationService);
    ReflectionTestUtils.setField(job, "batch", 100);
    ReflectionTestUtils.setField(job, "samplingRatePercent", 5);
    when(inspectionApplicationService.sampleIfEligible(anyLong(), anyInt()))
        .thenReturn(SampleOutcome.NOT_SAMPLED);
  }

  @Test
  @DisplayName("逐笔抽样并透传配置比例（比例写死会被此断言拦住）")
  void samplesEachCandidateWithConfiguredRate() {
    when(claimCaseRepository.findFinishedForInspectionAllTenants(100))
        .thenReturn(List.of(ref(1L, 7L), ref(2L, 8L)));

    job.sampleDaily();

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

    job.sampleDaily();

    verify(inspectionApplicationService, times(1)).sampleIfEligible(eq(1L), anyInt());
    verify(inspectionApplicationService, times(1)).sampleIfEligible(eq(2L), anyInt());
  }

  private static ClaimTenantRef ref(long claimId, long tenantId) {
    return new ClaimTenantRef(tenantId, claimId, "CL" + claimId, "FINISHED");
  }
}
