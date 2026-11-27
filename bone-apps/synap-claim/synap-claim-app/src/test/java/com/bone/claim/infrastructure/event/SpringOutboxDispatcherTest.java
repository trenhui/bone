package com.bone.claim.infrastructure.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.InsurerPushGatewayPort;
import com.bone.claim.application.port.out.InsurerPushGatewayPort.InsurerPushMessage;
import com.bone.claim.domain.port.out.OutboxRecord;
import com.bone.core.domain.event.DomainEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 发件箱派发介质路由测试：INSURER_PUSH 在配置端点后走 HTTP，其余事件仍走进程内总线。 */
@ExtendWith(MockitoExtension.class)
class SpringOutboxDispatcherTest {

  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private InsurerPushGatewayPort insurerPushGatewayPort;

  private SpringOutboxDispatcher dispatcher() {
    return new SpringOutboxDispatcher(
        domainEventPublisher, new ObjectMapper(), insurerPushGatewayPort);
  }

  private static OutboxRecord pushRecord() {
    return new OutboxRecord(
        0L, "ClaimCase", 42L, "INSURER_PUSH", "{\"claimNo\":\"CL1\",\"amount\":\"100.00\"}");
  }

  @Test
  void configuredInsurerPushGoesThroughHttpPort() {
    when(insurerPushGatewayPort.isConfigured()).thenReturn(true);
    when(insurerPushGatewayPort.push(any())).thenReturn("R-1");

    dispatcher().dispatch(pushRecord());

    verify(insurerPushGatewayPort).push(any(InsurerPushMessage.class));
    verify(domainEventPublisher, never()).publish(any());
  }

  @Test
  void unconfiguredInsurerPushFallsBackToInProcessBus() {
    when(insurerPushGatewayPort.isConfigured()).thenReturn(false);

    dispatcher().dispatch(pushRecord());

    verify(insurerPushGatewayPort, never()).push(any());
    verify(domainEventPublisher).publish(any());
  }

  @Test
  void otherEventsAlwaysUseInProcessBus() {
    dispatcher().dispatch(new OutboxRecord(0L, "ClaimCase", 42L, "CLAIM_PAYMENT_SUCCEEDED", "{}"));

    verify(insurerPushGatewayPort, never()).push(any());
    verify(domainEventPublisher).publish(any());
  }
}
