package com.bone.claim.adapter.schedule;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.SlaApplicationService;
import com.bone.claim.domain.model.claim.projection.ClaimTenantRef;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** SLA 物化 Job 测试：全租户扫描 + 逐笔携带租户 + 单笔异常续跑。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshLimitHourJobTest {

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private SlaApplicationService slaApplicationService;

  private RefreshLimitHourJob job;

  @BeforeEach
  void setUp() {
    job = new RefreshLimitHourJob(claimCaseRepository, slaApplicationService);
    ReflectionTestUtils.setField(job, "batch", 500);
  }

  @Test
  void refreshesEachActiveClaim() {
    when(claimCaseRepository.findActiveClaimsAllTenants(anyInt()))
        .thenReturn(
            List.of(
                new ClaimTenantRef(7L, 11L, "C1", "ADJUSTING"),
                new ClaimTenantRef(8L, 12L, "C2", "PENDING_ADJUST")));

    job.refreshLimitHour();

    verify(slaApplicationService).refreshForClaim(11L);
    verify(slaApplicationService).refreshForClaim(12L);
  }

  @Test
  void continuesWithRemainingClaimsWhenOneThrows() {
    when(claimCaseRepository.findActiveClaimsAllTenants(anyInt()))
        .thenReturn(
            List.of(
                new ClaimTenantRef(7L, 11L, "C1", "ADJUSTING"),
                new ClaimTenantRef(8L, 12L, "C2", "PENDING_ADJUST")));
    doThrow(new RuntimeException("boom")).when(slaApplicationService).refreshForClaim(11L);

    job.refreshLimitHour();

    verify(slaApplicationService, times(2)).refreshForClaim(anyLong());
  }
}
