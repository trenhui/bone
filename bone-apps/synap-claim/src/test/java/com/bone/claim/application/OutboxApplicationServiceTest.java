package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.event.DomainEventOutbox;
import com.bone.claim.domain.port.out.OutboxPort;
import com.bone.claim.domain.port.out.OutboxRecord;
import com.bone.claim.domain.repository.DomainEventOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * {@link OutboxApplicationService#dispatchAndAck} 的派发语义（B3 防重复 / B2 失败回退）。
 *
 * <p><b>关键不变量</b>：同一笔 PENDING 事件在多副本并发或事务回滚下<b>至多派发一次</b>，且派发失败须回退到 PENDING 待重投（而非静默丢失）。
 */
@ExtendWith(MockitoExtension.class)
class OutboxApplicationServiceTest {

  @Mock DomainEventOutboxRepository outboxRepository;
  @Mock OutboxPort outboxPort;

  @InjectMocks OutboxApplicationService service;

  private static DomainEventOutbox pending(long id, long tenantId) {
    return DomainEventOutbox.of(tenantId, "AGG", id, "EVENT", "{\"k\":1}");
  }

  @Test
  void dispatchesPendingAndMarksSent() {
    DomainEventOutbox o = pending(1L, 1L);
    when(outboxRepository.load(1L, 1L)).thenReturn(o);
    when(outboxRepository.save(any(DomainEventOutbox.class))).thenReturn(1L);

    boolean ok = service.dispatchAndAck(1L, 1L);

    assertThat(ok).isTrue();
    verify(outboxPort).dispatch(any(OutboxRecord.class));
    assertThat(o.getStatus()).isEqualTo(DomainEventOutbox.STATUS_SENT);
  }

  @Test
  void skipsWhenAlreadySent() {
    DomainEventOutbox o = pending(1L, 1L);
    o.markSent();
    when(outboxRepository.load(1L, 1L)).thenReturn(o);

    boolean ok = service.dispatchAndAck(1L, 1L);

    assertThat(ok).isTrue();
    verify(outboxPort, never()).dispatch(any());
  }

  @Test
  void concurrentClaimPreventsDoubleDispatch() {
    DomainEventOutbox o = pending(1L, 1L);
    when(outboxRepository.load(1L, 1L)).thenReturn(o);
    // 占用态 save 抛乐观锁冲突，模拟被其他实例抢走
    when(outboxRepository.save(any())).thenThrow(new OptimisticLockingFailureException("已被其他实例抢占"));

    boolean ok = service.dispatchAndAck(1L, 1L);

    assertThat(ok).isTrue(); // 视为已被其他实例接管
    verify(outboxPort, never()).dispatch(any()); // 未重复派发
  }

  @Test
  void dispatchFailureRevertsAndReturnsFalse() {
    DomainEventOutbox o = pending(1L, 1L);
    when(outboxRepository.load(1L, 1L)).thenReturn(o);
    when(outboxRepository.save(any(DomainEventOutbox.class))).thenReturn(1L);
    doThrow(new RuntimeException("broker down")).when(outboxPort).dispatch(any());

    boolean ok = service.dispatchAndAck(1L, 1L);

    assertThat(ok).isFalse();
    assertThat(o.getStatus()).isEqualTo(DomainEventOutbox.STATUS_PENDING); // 未耗尽重试 → 回退 PENDING 待重投
    verify(outboxPort).dispatch(any());
  }
}
