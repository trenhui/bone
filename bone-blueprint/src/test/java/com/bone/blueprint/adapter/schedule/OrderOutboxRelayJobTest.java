package com.bone.blueprint.adapter.schedule;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.OrderOutboxRelayPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Outbox 中继任务测试。
 *
 * <p>Job 只是触发器：真正的中继语义（至少一次、重试、死信）在 {@code OrderOutboxRelay}，此处只保证「每轮都调用中继端口」 ——若 Job
 * 被改成依赖租户上下文或加了前置判断，中继就可能静默停摆。
 */
@ExtendWith(MockitoExtension.class)
class OrderOutboxRelayJobTest {

  @Mock private OrderOutboxRelayPort orderOutboxRelayPort;

  @InjectMocks private OrderOutboxRelayJob job;

  @Test
  void relaysPendingMessagesEveryRound() {
    when(orderOutboxRelayPort.relayPending()).thenReturn(3);

    job.relay();

    verify(orderOutboxRelayPort).relayPending();
  }

  @Test
  void toleratesEmptyBatch() {
    when(orderOutboxRelayPort.relayPending()).thenReturn(0);

    job.relay();

    verify(orderOutboxRelayPort).relayPending();
  }
}
