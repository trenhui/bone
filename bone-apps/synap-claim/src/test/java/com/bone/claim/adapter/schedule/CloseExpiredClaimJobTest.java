package com.bone.claim.adapter.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.domain.model.claim.projection.ClaimTenantRef;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 超期关单定时任务测试（同样锁住全租户扫描 + 关闭动作显式携带租户）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CloseExpiredClaimJobTest {

  @Mock private ClaimCaseRepository claimCaseRepository;

  @Mock private ClaimCaseApplicationService claimCaseApplicationService;

  private CloseExpiredClaimJob job;

  @BeforeEach
  void setUp() {
    job = new CloseExpiredClaimJob(claimCaseRepository, claimCaseApplicationService);
    // @Value 字段由 Spring 在运行期注入，单测里手工置入（默认 45，与 @Value 占位符默认值一致）
    ReflectionTestUtils.setField(job, "staleEvidenceDays", 45L);
  }

  @Test
  void closesEachStaleClaimCarryingItsOwnTenant() {
    when(claimCaseRepository.findStaleEvidenceClaimsAllTenants(any()))
        .thenReturn(List.of(ref(0L, 11L), ref(888L, 12L)));

    job.closeStaleClaims();

    ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
    verify(claimCaseApplicationService, times(2)).closeForStale(captor.capture(), any());
    assertEquals(List.of(11L, 12L), captor.getAllValues());
  }

  @Test
  void continuesWithRemainingRowsWhenOneFails() {
    when(claimCaseRepository.findStaleEvidenceClaimsAllTenants(any()))
        .thenReturn(List.of(ref(0L, 11L), ref(0L, 12L)));
    doThrow(new RuntimeException("status conflict"))
        .when(claimCaseApplicationService)
        .closeForStale(anyLong(), any());

    job.closeStaleClaims();

    verify(claimCaseApplicationService, times(2)).closeForStale(anyLong(), any());
  }

  private static ClaimTenantRef ref(Long tenantId, Long claimId) {
    return new ClaimTenantRef(tenantId, claimId, "CASE-" + claimId, "21");
  }
}
