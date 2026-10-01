package com.bone.integration.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.tenant.context.TenantContext;
import com.bone.integration.application.config.IntegrationOutboxProperties;
import com.bone.integration.application.event.port.IntegrationMessageSender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Outbox 中继租户上下文回归测试。
 *
 * <p>中继跑在调度线程，无租户会话；更新 int_outbox（租户作用域表）若不落入记录自身租户，SDK 会 fail-closed 抛 {@code
 * MissingTenantContextException}，导致 SENT 状态永不落库（历史缺陷）。
 */
@ExtendWith(MockitoExtension.class)
class IntegrationOutboxRelayTest {

  @Mock IntegrationOutboxRepository outboxRepository;
  @Mock IntegrationMessageSender messageSender;

  IntegrationOutboxProperties properties;
  IntegrationOutboxRelay relay;

  @BeforeEach
  void setUp() {
    properties = new IntegrationOutboxProperties();
    relay = new IntegrationOutboxRelay(properties, outboxRepository, messageSender);
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private IntegrationOutboxRecord pendingRecord(Long tenantId) {
    return IntegrationOutboxRecord.pending(
        1L, tenantId, "evt-1", "OrderPaid", "bone-order", "key-1", "{}");
  }

  @Test
  void relayUpdatesWithinRecordTenantContext() {
    IntegrationOutboxRecord record = pendingRecord(1001L);
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(record));
    // 模拟 SDK 租户作用域表语义：无租户上下文时更新必须失败
    doAnswer(
            invocation -> {
              assertThat(TenantContext.getTenantIdAsLong())
                  .as("更新 int_outbox 必须在记录自身租户上下文内执行")
                  .isEqualTo(1001L);
              return true;
            })
        .when(outboxRepository)
        .update(any(IntegrationOutboxRecord.class));

    int sent = relay.relayBatch();

    assertThat(sent).isEqualTo(1);
    assertThat(record.getStatus()).isEqualTo(OutboxStatus.SENT);
    // 中继结束后不得残留租户上下文（调度线程复用）
    assertThat(TenantContext.getTenantId()).isNull();
  }

  @Test
  void relayFailureDoesNotAbortBatchAndStaysInTenantContext() {
    IntegrationOutboxRecord first = pendingRecord(1001L);
    IntegrationOutboxRecord second = pendingRecord(2002L);
    when(outboxRepository.findByCriteria(any())).thenReturn(List.of(first, second));
    doThrow(new RuntimeException("mq down")).when(messageSender).send(any(), any(), any());
    doAnswer(
            invocation -> {
              // 失败路径的重试计数更新同样必须在记录自身租户上下文内
              assertThat(TenantContext.getTenantIdAsLong()).isNotNull();
              return true;
            })
        .when(outboxRepository)
        .update(any(IntegrationOutboxRecord.class));

    int sent = relay.relayBatch();

    assertThat(sent).isZero();
    // MQ 全部失败：两条各自完成重试计数更新（互不中断），均保持 PENDING
    assertThat(first.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(first.getRetryCount()).isEqualTo(1);
    assertThat(second.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(second.getRetryCount()).isEqualTo(1);
    verify(outboxRepository, times(2)).update(argThat(r -> r != null));
    assertThat(TenantContext.getTenantId()).isNull();
  }
}
