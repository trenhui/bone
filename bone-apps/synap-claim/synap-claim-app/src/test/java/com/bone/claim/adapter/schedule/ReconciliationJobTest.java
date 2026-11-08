package com.bone.claim.adapter.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ReconciliationApplicationService;
import com.bone.claim.domain.model.reconciliation.ReconTenantRef;
import com.bone.claim.domain.repository.ReconciliationReportRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 对账 Job 测试：全租户枚举 + 逐租户携带上下文 + 单租户异常续跑。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconciliationJobTest {

  @Mock private ReconciliationReportRepository reportRepository;
  @Mock private ReconciliationApplicationService reconciliationApplicationService;

  private ReconciliationJob job;

  @BeforeEach
  void setUp() {
    job = new ReconciliationJob(reportRepository, reconciliationApplicationService);
    ReflectionTestUtils.setField(job, "bizDateOffsetDays", 1L);
  }

  @Test
  void reconcilesEachTenant() {
    when(reportRepository.findTenantIdsAllTenants())
        .thenReturn(List.of(new ReconTenantRef(7L), new ReconTenantRef(8L)));

    job.reconcileDaily();

    verify(reconciliationApplicationService, times(2)).reconcileForTenant(any(LocalDate.class));
  }

  @Test
  void continuesWithRemainingTenantsWhenOneThrows() {
    when(reportRepository.findTenantIdsAllTenants())
        .thenReturn(List.of(new ReconTenantRef(7L), new ReconTenantRef(8L)));
    doThrow(new RuntimeException("boom"))
        .when(reconciliationApplicationService)
        .reconcileForTenant(any(LocalDate.class));

    job.reconcileDaily();

    verify(reconciliationApplicationService, times(2)).reconcileForTenant(any(LocalDate.class));
  }
}
