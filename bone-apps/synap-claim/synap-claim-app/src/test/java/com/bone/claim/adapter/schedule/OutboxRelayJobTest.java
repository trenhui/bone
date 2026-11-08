package com.bone.claim.adapter.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.OutboxApplicationService;
import com.bone.claim.domain.model.event.DomainEventOutbox;
import com.bone.claim.domain.repository.DomainEventOutboxRepository;
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

/** 发件箱中继测试（同样锁住全租户扫描 + 关闭动作显式携带租户）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxRelayJobTest {

  @Mock private DomainEventOutboxRepository outboxRepository;

  @Mock private OutboxApplicationService outboxApplicationService;

  private OutboxRelayJob job;

  @BeforeEach
  void setUp() {
    job = new OutboxRelayJob(outboxRepository, outboxApplicationService);
    ReflectionTestUtils.setField(job, "olderThanMinutes", 60L);
    ReflectionTestUtils.setField(job, "batch", 200);
  }

  @Test
  void dispatchesEachPendingRowCarryingItsOwnTenant() {
    DomainEventOutbox r1 = ref(0L, 11L);
    DomainEventOutbox r2 = ref(888L, 12L);
    when(outboxRepository.findPendingOutboxAllTenants(any(), anyInt())).thenReturn(List.of(r1, r2));
    when(outboxApplicationService.dispatchAndAck(anyLong(), anyLong())).thenReturn(true);

    job.relayPending();

    ArgumentCaptor<Long> tenantCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
    verify(outboxApplicationService, times(2))
        .dispatchAndAck(tenantCaptor.capture(), idCaptor.capture());
    // E-2：每行的租户必须显式带入派发，不能依赖线程上下文
    assertEquals(List.of(0L, 888L), tenantCaptor.getAllValues());
    assertEquals(List.of(11L, 12L), idCaptor.getAllValues());
  }

  @Test
  void countsFailureWhenDispatchAckReturnsFalse() {
    DomainEventOutbox r1 = ref(0L, 11L);
    DomainEventOutbox r2 = ref(0L, 12L);
    when(outboxRepository.findPendingOutboxAllTenants(any(), anyInt())).thenReturn(List.of(r1, r2));
    when(outboxApplicationService.dispatchAndAck(anyLong(), anyLong())).thenReturn(false);

    job.relayPending();

    verify(outboxApplicationService, times(2)).dispatchAndAck(anyLong(), anyLong());
  }

  @Test
  void continuesWithRemainingRowsWhenOneThrows() {
    DomainEventOutbox r1 = ref(0L, 11L);
    DomainEventOutbox r2 = ref(0L, 12L);
    when(outboxRepository.findPendingOutboxAllTenants(any(), anyInt())).thenReturn(List.of(r1, r2));
    doThrow(new RuntimeException("boom"))
        .when(outboxApplicationService)
        .dispatchAndAck(anyLong(), anyLong());

    job.relayPending();

    verify(outboxApplicationService, times(2)).dispatchAndAck(anyLong(), anyLong());
  }

  private static DomainEventOutbox ref(long tenantId, long id) {
    DomainEventOutbox outbox = mock(DomainEventOutbox.class);
    when(outbox.getTenantId()).thenReturn(tenantId);
    when(outbox.getId()).thenReturn(id);
    return outbox;
  }
}
